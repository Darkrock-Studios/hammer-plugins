import hammer.exporter
import kotlin.wasm.WasmExport

@WasmExport
fun export(): Int = exporter { markdownExport(it).encodeToByteArray() }
