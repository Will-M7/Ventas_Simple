const API_URL = 'http://localhost:3000/api';

const sellers = [
  { username: 'vendedor1', password: '123', nombre: 'Carlos Mendoza', rol: 'vendedor' },
  { username: 'vendedor2', password: '123', nombre: 'Ana Torres', rol: 'vendedor' },
  { username: 'vendedor3', password: '123', nombre: 'Luis Flores', rol: 'vendedor' },
  { username: 'vendedor4', password: '123', nombre: 'María Gómez', rol: 'vendedor' }
];

const products = [
  'Gaseosa Coca-Cola 500ml', 'Inka Kola 500ml', 'Agua San Luis 625ml', 'Jugo Frugos Durazno 1L', 'Cerveza Cusqueña 330ml',
  'Arroz Extra 1kg', 'Azúcar Rubia 1kg', 'Aceite Primor 1L', 'Fideos Don Vittorio 500g', 'Harina Preparada 1kg',
  'Papas Lays Clasicas 160g', 'Galletas Soda Field 360g', 'Chocolatina Sublime 30g', 'Doritos Queso 150g', 'Cheetos Queso 120g',
  'Detergente Ace 800g', 'Jabón Camay 125g', 'Lavavajillas Ayudín 500g', 'Lejía Clorox 1L', 'Limpiador Poett 900ml',
  'Leche Gloria Entera 400g', 'Yogurt Gloria Fresa 1L', 'Mantequilla Laive 200g', 'Queso Edam 250g', 'Crema de Leche Nestlé 200g',
  'Atún Primor Trozos 170g', 'Sal Marina 1kg', 'Avena Tres Ositos 500g', 'Café Nescafé 100g', 'Té Herbi Manzanilla 25u',
  'Galletas Casino Chocolate', 'Sublime Extremo 50g', 'Chocolate Princesa 30g', 'Mentitas Ambrosoli 50g', 'Caramelos Lemonhead',
  'Shampoo Head Shoulders 375ml', 'Acondicionador Pantene 400ml', 'Pasta Dental Kolynos 120g', 'Desodorante Rexona 150ml', 'Papel Higiénico Suave 4u',
  'Servilletas Elite 100u', 'Esponja Scotch-Brite', 'Bolsas de Basura 10u', 'Inka Chips Sal Marina 135g', 'Gaseosa Sprite 500ml',
  'Gaseosa Fanta 500ml', 'Electrolight Fresa 1L', 'Energizante Volt 300ml', 'Cerveza Pilsen Callao 630ml', 'Vino Tabernero 750ml'
];

async function seed() {
  console.log('--- Creando 4 Vendedores ---');
  for (const seller of sellers) {
    const res = await fetch(`${API_URL}/usuarios`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(seller)
    });
    console.log(`Usuario ${seller.username}: ${res.status === 201 ? 'Creado OK' : 'Ya existía o error'}`);
  }

  console.log('\n--- Cargando 50 Productos ---');
  let count = 0;
  for (let i = 0; i < products.length; i++) {
    const code = (7750000000001 + i).toString();
    const purchase = (2.5 + (i % 5)).toFixed(2);
    const sale = (parseFloat(purchase) * 1.3).toFixed(2);
    const stock = 20 + (i * 2);

    const res = await fetch(`${API_URL}/productos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        codigo_barras: code,
        nombre: products[i],
        precio_compra: parseFloat(purchase),
        precio_venta: parseFloat(sale),
        stock: stock
      })
    });
    if (res.ok) count++;
  }
  console.log(`\n¡Carga completada! ${count} productos registrados.`);
}

seed();
