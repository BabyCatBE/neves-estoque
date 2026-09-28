package com.babycatbe.nevesestoque.feature.home

data class HomeModule(val title: String, val mark: String, val route: String)

val homeModules = listOf(
    HomeModule("Estoque atual", "E", "estoque"),
    HomeModule("Conferência", "C", "conferencias"),
    HomeModule("Entrada", "+", "entradas"),
    HomeModule("Compras", "R$", "compras"),
    HomeModule("Produtos", "P", "produtos"),
    HomeModule("Fornecedores", "F", "fornecedores"),
)
