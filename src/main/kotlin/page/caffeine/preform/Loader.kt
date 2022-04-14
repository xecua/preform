package page.caffeine.preform


class Loader<T> {
    fun load(name: String): T? {
        val klass = loadClass(name) ?: return null
        return newInstance(klass)
    }

    fun newInstance(klass: Class<out T>): T? {
        try {
            return klass.getDeclaredConstructor().newInstance()
        } catch (e: ReflectiveOperationException) {
            throw java.lang.RuntimeException(e)
        }
    }

    fun loadClass(name: String): Class<out T>? {
        return tryLoadClass(name)
            ?: tryLoadClass(Loader::class.java.`package`.name + ".filters." + name)
            ?: tryLoadClass("jp.ac.titech.c.se.stein.app.$name")

    }

    fun tryLoadClass(name: String): Class<out T>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(name) as Class<out T>
        } catch (e: Exception) {
            when (e) {
                is ClassNotFoundException, is ClassCastException -> null
                else -> throw e
            }

        }
    }

}
