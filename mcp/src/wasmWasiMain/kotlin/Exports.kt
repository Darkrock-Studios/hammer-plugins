import hammer.command
import kotlin.wasm.WasmExport

@WasmExport
fun command(): Int = command(::mcpReply)
