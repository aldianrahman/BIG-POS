package com.berdikariintigemilang.pos.data.pricing

import com.berdikariintigemilang.pos.data.cart.CartLine
import com.berdikariintigemilang.pos.data.local.CatalogDao
import com.berdikariintigemilang.pos.data.remote.AppliedBundleDto
import com.berdikariintigemilang.pos.data.remote.BundleCalcResult
import com.berdikariintigemilang.pos.data.remote.ReceiptSettingDto
import com.berdikariintigemilang.pos.data.remote.taxFor
import com.berdikariintigemilang.pos.data.remote.totalFor
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Hasil perhitungan harga lengkap dari isi keranjang. */
data class PricingResult(
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val bundleDiscount: Double = 0.0,
    val appliedBundles: List<AppliedBundleDto> = emptyList(),
    val taxAmount: Double = 0.0,
    val taxInclusive: Boolean = true,
    val total: Double = 0.0
)

/**
 * Menghitung potongan bundle & PPN sepenuhnya dari data yang sudah di-cache,
 * tanpa memanggil server. Algoritma bundle adalah replikasi persis dari
 * PosBundleService.calculateForCart di backend (greedy: potongan terbesar
 * diterapkan lebih dulu, kelipatan, qty keranjang "dikonsumsi" agar tak dobel),
 * sehingga total yang ditagih offline sama dengan yang dihitung ulang server
 * saat sinkronisasi.
 */
@Singleton
class LocalPricingCalculator @Inject constructor(
    private val catalogDao: CatalogDao
) {

    /** Pengaturan PPN dari cache (subset untuk perhitungan pajak). */
    suspend fun receiptSetting(): ReceiptSettingDto? {
        val e = catalogDao.receiptSetting() ?: return null
        return ReceiptSettingDto(
            taxEnabled = e.taxEnabled,
            taxPercent = e.taxPercent,
            taxInclusive = e.taxInclusive
        )
    }

    /** Potongan bundle untuk komposisi keranjang (productId -> total qty). */
    suspend fun calculateBundles(cartQty: Map<Long, Int>): BundleCalcResult {
        if (cartQty.isEmpty()) return BundleCalcResult(0.0, emptyList())

        val today = LocalDate.now()
        val activeBundles = catalogDao.activeBundles().filter { inRange(it.startDate, it.endDate, today) }
        if (activeBundles.isEmpty()) return BundleCalcResult(0.0, emptyList())

        val itemsByBundle = catalogDao.allBundleItems().groupBy { it.bundleId }
        val neededProductIds = activeBundles
            .flatMap { itemsByBundle[it.id] ?: emptyList() }
            .map { it.productId }
            .toSet()
        val products = catalogDao.productsByIds(neededProductIds.toList()).associateBy { it.id }

        data class Candidate(val bundleId: Long, val name: String, val items: List<Pair<Long, Int>>, val discountPerBundle: Double)

        val candidates = activeBundles.mapNotNull { b ->
            val items = itemsByBundle[b.id] ?: return@mapNotNull null
            if (items.isEmpty()) return@mapNotNull null
            // Semua produk penyusun harus ada di cache & aktif.
            if (items.any { products[it.productId]?.isActive != true }) return@mapNotNull null
            val normal = items.sumOf { products.getValue(it.productId).sellingPrice * it.quantity }
            val disc = normal - b.bundlePrice
            if (disc <= 0.0) return@mapNotNull null
            Candidate(b.id, b.name, items.map { it.productId to it.quantity }, disc)
        }.sortedByDescending { it.discountPerBundle }

        val remaining = HashMap(cartQty)
        var totalDiscount = 0.0
        val applied = mutableListOf<AppliedBundleDto>()

        for (c in candidates) {
            val count = c.items.minOf { (productId, qty) -> (remaining[productId] ?: 0) / qty }
            if (count <= 0) continue
            c.items.forEach { (productId, qty) -> remaining[productId] = (remaining[productId] ?: 0) - count * qty }
            val discount = c.discountPerBundle * count
            totalDiscount += discount
            applied.add(AppliedBundleDto(c.bundleId, c.name, count, discount))
        }
        return BundleCalcResult(totalDiscount, applied)
    }

    /** Hitung penuh: subtotal, bundle, PPN, dan total dari keranjang + diskon nominal (Rp). */
    suspend fun price(lines: List<CartLine>, discount: Double): PricingResult {
        val subtotal = lines.sumOf { it.lineSubtotal }
        val cartQty = HashMap<Long, Int>()
        lines.forEach { cartQty.merge(it.productId, it.quantity, Int::plus) }
        val bundle = calculateBundles(cartQty)
        val taxCfg = receiptSetting()
        val base = (subtotal - discount - bundle.bundleDiscount).coerceAtLeast(0.0)
        return PricingResult(
            subtotal = subtotal,
            discount = discount,
            bundleDiscount = bundle.bundleDiscount,
            appliedBundles = bundle.appliedBundles,
            taxAmount = taxCfg.taxFor(base),
            taxInclusive = taxCfg?.taxInclusive ?: true,
            total = taxCfg.totalFor(base)
        )
    }

    private fun inRange(startDate: String?, endDate: String?, today: LocalDate): Boolean {
        val start = startDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = endDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return (start == null || !start.isAfter(today)) && (end == null || !end.isBefore(today))
    }
}
