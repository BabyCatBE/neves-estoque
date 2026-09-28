package com.babycatbe.nevesestoque.feature.suppliers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupplierRow(
    val id: String,
    val name: String,
    val company: String? = null,
    val phone: String? = null,
    val observation: String? = null,
    @SerialName("purchase_frequency_days") val purchaseFrequencyDays: Int? = null,
    @SerialName("preferred_order_weekday") val preferredOrderWeekday: Int? = null,
    @SerialName("average_delivery_days") val averageDeliveryDays: Int? = null,
    @SerialName("safety_margin_days") val safetyMarginDays: Int? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

data class SupplierDetails(
    val id: String,
    val name: String,
    val company: String?,
    val phone: String?,
    val observation: String?,
    val purchaseFrequencyDays: Int?,
    val preferredOrderWeekday: Int?,
    val averageDeliveryDays: Int?,
    val safetyMarginDays: Int?,
    val isPending: Boolean,
)

data class SupplierMutationInput(
    val name: String,
    val company: String,
    val phone: String,
    val observation: String?,
    val purchaseFrequencyDays: Int?,
    val preferredOrderWeekday: Int?,
    val averageDeliveryDays: Int?,
    val safetyMarginDays: Int?,
)

fun SupplierRow.toDetails() = SupplierDetails(
    id = id,
    name = name,
    company = company,
    phone = phone,
    observation = observation,
    purchaseFrequencyDays = purchaseFrequencyDays,
    preferredOrderWeekday = preferredOrderWeekday,
    averageDeliveryDays = averageDeliveryDays,
    safetyMarginDays = safetyMarginDays,
    isPending = company.isNullOrBlank() || !isSupplierPhoneComplete(phone),
)
