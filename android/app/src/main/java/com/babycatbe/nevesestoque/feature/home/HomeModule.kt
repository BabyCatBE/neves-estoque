package com.babycatbe.nevesestoque.feature.home

data class HomeModule(
    val title: String,
    val mark: String,
)

val homeModules = listOf(
    HomeModule(title = "Estoque atual", mark = "E"),
    HomeModule(title = "Conferência", mark = "C"),
    HomeModule(title = "Entrada", mark = "+"),
    HomeModule(title = "Compras", mark = "R$"),
    HomeModule(title = "Produtos", mark = "P"),
    HomeModule(title = "Fornecedores", mark = "F"),
)
