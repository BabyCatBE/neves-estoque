package com.babycatbe.nevesestoque.feature.home

data class HomeModule(val title: String, val route: String)

val homeModules = listOf(
    HomeModule("Estoque atual", "estoque"),
    HomeModule("Conferência", "conferencias"),
    HomeModule("Entrada", "entradas"),
    HomeModule("Compras", "compras"),
    HomeModule("Produtos", "produtos"),
    HomeModule("Fornecedores", "fornecedores"),
)

