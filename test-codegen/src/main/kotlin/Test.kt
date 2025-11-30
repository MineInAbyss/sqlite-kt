import com.mineinabyss.sqlite.generated.HelloWorld
import me.dvyy.sqlite.Database

suspend fun main() {
    val db = Database.temporary()
    val example = HelloWorld()
    db.write {
        example.getSpawnsNear(x = 0, y = 0, z = 0, rad = 10.0).map {
            getText(it.data)
            getInt(it.id)
        }
        example.insertData(id = 1, data = "Hello World!")
        example.deleteSpawnsOlderThan(epochSeconds = 100)
    }
}