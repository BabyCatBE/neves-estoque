package com.babycatbe.nevesestoque.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeModuleTest {
    @Test
    fun homeKeepsTheSixApprovedV1Modules() {
        assertEquals(
            listOf(
                "Estoque atual",
                "Conferência",
                "Entrada",
                "Compras",
                "Produtos",
                "Fornecedores",
            ),
            homeModules.map { it.title },
        )
    }
}
