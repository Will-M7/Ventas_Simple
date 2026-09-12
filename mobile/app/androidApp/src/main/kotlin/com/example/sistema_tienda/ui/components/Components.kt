package com.example.sistema_tienda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.sistema_tienda.data.AppContainer
import com.example.sistema_tienda.domain.model.Product
import java.util.Locale

fun money(value: Double): String = "S/ %.2f".format(Locale.US, value)

@Composable fun LoadingState() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable fun EmptyState(message: String) { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable fun ErrorState(message: String, retry: (() -> Unit)? = null) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.error); retry?.let { TextButton(onClick = it) { Text("Reintentar") } } } }
@Composable fun StatCard(title: String, value: String, modifier: Modifier = Modifier) { Card(modifier, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(6.dp)); Text(value, style = MaterialTheme.typography.headlineSmall) } } }
@Composable fun ProductImage(product: Product, c: AppContainer, modifier: Modifier = Modifier) { if (product.imagen.isNullOrBlank()) Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text("📦", style = MaterialTheme.typography.headlineMedium) } else AsyncImage(model = if (product.imagen!!.startsWith("http")) product.imagen else c.serverAssetsUrl(product.imagen), contentDescription = product.nombre, modifier = modifier) }
@Composable fun ProductCard(product: Product, c: AppContainer, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { ProductImage(product, c, Modifier.size(58.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(product.nombre, style = MaterialTheme.typography.titleMedium); Text(product.codigo_barras, style = MaterialTheme.typography.bodySmall); Text("${money(product.precio_venta)} · stock ${product.stock}", style = MaterialTheme.typography.bodyMedium) }; if (!product.activo) Text("Inactivo", color = MaterialTheme.colorScheme.error) } } }
@Composable fun QuantitySelector(value: Int, onChange: (Int) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = { if (value > 1) onChange(value - 1) }) { Text("−") }; Text(value.toString(), Modifier.padding(horizontal = 14.dp)); OutlinedButton(onClick = { onChange(value + 1) }) { Text("+") } } }
