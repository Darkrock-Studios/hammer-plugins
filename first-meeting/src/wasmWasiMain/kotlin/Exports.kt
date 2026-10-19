import hammer.action
import kotlin.wasm.WasmExport

@WasmExport
fun action(): Int = action { meet(it) }
