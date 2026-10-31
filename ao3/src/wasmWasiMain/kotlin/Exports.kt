import hammer.exporter
import kotlin.wasm.WasmExport

@WasmExport
fun export(): Int = exporter { ao3Export(it).encodeToByteArray() }
