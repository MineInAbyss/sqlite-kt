import me.dvyy.sqlite.Database
import me.dvyy.sqlite.generated.MainDatabase

suspend fun main() {
    val db = Database.temporary()
    val schema = MainDatabase()
    db.write {
        schema.create()
        val queries = schema.other
        queries.test()
        println("Wrote!")
    }
}
