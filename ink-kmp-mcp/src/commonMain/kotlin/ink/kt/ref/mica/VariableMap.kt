package ink.kt.ref.mica

/** The interface for the story object.
 *
  */
interface VariableMap {
  fun logException(e: Exception)
  fun hasValue(token: String): Boolean
  fun getValue(token: String): Any
  fun hasFunction(token: String): Boolean
  fun getFunction(token: String): Function
  fun hasGameObject(token: String): Boolean
  fun debugInfo(): String
}
