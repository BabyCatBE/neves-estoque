package com.babycatbe.nevesestoque.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeModuleTest {
    @Test
    fun homeKeepsTheSixApprovedV1Modules() {
        assertEquals(
            listOf("Estoque atual", "Conferência", "Entrada", "Compras", "Produtos", "Fornecedores"),
            homeModules.map { it.title },
        )
    }

    @Test
    fun routesAreStableAndUnique() {
        assertEquals(
            listOf("estoque", "conferencias", "entradas", "compras", "produtos", "fornecedores"),
            homeModules.map { it.route },
        )
        assertEquals(homeModules.size, homeModules.map { it.route }.distinct().size)
    }
}
