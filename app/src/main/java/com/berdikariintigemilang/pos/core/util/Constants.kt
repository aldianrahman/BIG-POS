package com.berdikariintigemilang.pos.core.util

object Constants {
    /** Role yang diizinkan login ke aplikasi POS. */
    val ALLOWED_ROLES = setOf("ROLE_DEVELOPER", "ROLE_KASIR", "ROLE_KASIR_ADMIN")

    /** Role yang dianggap admin (boleh CRUD master data, void). */
    val ADMIN_ROLES = setOf("ROLE_DEVELOPER", "ROLE_KASIR_ADMIN")

    /**
     * ID karyawan (user big-tracker) yang berwenang mengubah harga jual di
     * halaman kasir. Hanya kredensial milik salah satu id ini yang boleh
     * menurunkan harga satuan saat transaksi. Ubah daftar di sini bila perlu.
     */
    val PRICE_EDIT_AUTHORIZED_IDS = setOf(38L, 54L, 60L)
     * ID record versi aplikasi BIG-POS di server, dipakai untuk gating versi
     * saat login (GET api/v1/app-versions/{id}).
     */
    const val APP_VERSION_ID = 16L
}

fun List<String>.isAllowedToLogin(): Boolean = any { it in Constants.ALLOWED_ROLES }
fun List<String>.isPosAdmin(): Boolean = any { it in Constants.ADMIN_ROLES }
