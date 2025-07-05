package com.hung.landplanggmap.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddLandDialog(
    area: Long,
    onDismiss: () -> Unit,
    onSave: (ownerName: String, phone: String, landType: Int, address: String) -> Unit // Thêm address
) {
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var landType by remember { mutableStateOf(1) } // Mặc định là 1 (Đất thổ cư)
    var expanded by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var ownerError by remember { mutableStateOf<String?>(null) }

    val landTypes = listOf("Đất thổ cư", "Đất thổ canh", "Loại khác")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhập thông tin thửa đất") },
        text = {
            Column {
                // Trường nhập tên chủ đất
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = {
                        ownerName = it.trim()
                        ownerError = null // Reset lỗi khi nhập lại
                    },
                    label = { Text("Tên chủ đất") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = ownerError != null,
                    supportingText = {
                        if (ownerError != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    contentDescription = "Lỗi",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    ownerError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Trường nhập số điện thoại
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.trim() // Loại bỏ khoảng trắng ở đầu và cuối
                        phoneError = null // Reset lỗi khi nhập lại
                    },
                    label = { Text("Số điện thoại") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = phoneError != null,
                    supportingText = {
                        if (phoneError != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    contentDescription = "Lỗi",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    phoneError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Dropdown chọn loại đất
                Box {
                    OutlinedTextField(
                        value = landTypes[landType - 1], // Hiển thị loại đất tương ứng
                        onValueChange = {},
                        label = { Text("Loại đất") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Dropdown",
                                modifier = Modifier.clickable { expanded = true }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        landTypes.forEachIndexed { index, type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    landType = index + 1 // Lưu giá trị 1, 2, 3 giống Spinner
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Hiển thị thông tin diện tích (không cho chỉnh sửa)
                Text("Diện tích: $area m²", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Kiểm tra tên chủ đất
                    if (ownerName.isBlank()) {
                        ownerError = "Tên chủ đất không được để trống."
                        return@Button
                    }

                    // Kiểm tra số điện thoại
                    val cleanedPhone = phone.replace(Regex("[^0-9]"), "") // Chỉ giữ chữ số
                    when {
                        cleanedPhone.length != 10 -> {
                            phoneError = "Số điện thoại phải đủ 10 chữ số."
                        }
                        !cleanedPhone.matches(Regex("^[0-9]+$")) -> {
                            phoneError = "Số điện thoại chỉ được chứa chữ số."
                        }
                        else -> {
                            phoneError = null
                            ownerError = null
                            onSave(ownerName, cleanedPhone, landType, "") // Gửi address trống tạm thời
                        }
                    }
                },
                enabled = ownerName.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}