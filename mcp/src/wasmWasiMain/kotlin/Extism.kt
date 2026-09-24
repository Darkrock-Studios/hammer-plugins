@file:OptIn(ExperimentalWasmInterop::class)

import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.WasmImport

// The Extism host functions this plugin uses, and Hammer's dispatch. Text crosses as UTF-8 in
// Extism memory blocks, copied a byte at a time.

@WasmImport("extism:host/env", "input_length")
private external fun inputLength(): Long

@WasmImport("extism:host/env", "input_load_u8")
private external fun inputLoadU8(offset: Long): Int

@WasmImport("extism:host/env", "alloc")
private external fun alloc(size: Long): Long

@WasmImport("extism:host/env", "length")
private external fun length(block: Long): Long

@WasmImport("extism:host/env", "load_u8")
private external fun loadU8(offset: Long): Int

@WasmImport("extism:host/env", "store_u8")
private external fun storeU8(offset: Long, value: Int)

@WasmImport("extism:host/env", "output_set")
private external fun outputSet(block: Long, size: Long)

@WasmImport("extism:host/env", "error_set")
private external fun errorSet(block: Long)

@WasmImport("extism:host/user", "hammer_dispatch")
private external fun hammerDispatch(request: Long): Long

fun readInput(): String = ByteArray(inputLength().toInt()) { inputLoadU8(it.toLong()).toByte() }.decodeToString()

fun writeOutput(text: String) {
	val block = write(text)
	outputSet(block, length(block))
}

fun setError(message: String) = errorSet(write(message))

/** Runs a Hammer operation; returns the reply JSON. */
fun dispatch(requestJson: String): String = read(hammerDispatch(write(requestJson)))

private fun write(text: String): Long {
	val bytes = text.encodeToByteArray()
	val block = alloc(bytes.size.toLong())
	bytes.forEachIndexed { i, byte -> storeU8(block + i, byte.toInt()) }
	return block
}

private fun read(block: Long): String = ByteArray(length(block).toInt()) { loadU8(block + it).toByte() }.decodeToString()
