package com.hung.landplanggmap.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddLandDialog(
    area: Long,
    onDismiss: () -> Unit,
    onSave: (ownerName: String, phone: String, address: String, landType: Int) -> Unit
) {
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var landType by remember { mutableStateOf(1) }
    var expanded by remember { mutableStateOf(false) }

    val landTypes = listOf("Đất thổ cư", "Đất thổ cảnh", "Loại khác")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhập thông tin thửa đất") },
        text = {
            Column {
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Tên chủ đất") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Số điện thoại") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Địa chỉ") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Dropdown chọn loại đất
                Box {
                    OutlinedTextField(
                        value = landTypes[landType - 1],
                        onValueChange = {},
                        label = { Text("Loại đất") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        readOnly = true
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        landTypes.forEachIndexed { index, type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    landType = index + 1
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Diện tích: $area m²")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ownerName, phone, address, landType)
                },
                enabled = ownerName.isNotBlank() && phone.isNotBlank() && address.isNotBlank()
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