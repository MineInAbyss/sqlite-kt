import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.annotations.WithSpan
import me.dvyy.sqlite.Database
import me.dvyy.sqlite.generated.MainDatabase
import kotlin.random.Random

@WithSpan
suspend fun main() {
    val span = GlobalOpenTelemetry.getTracer("application").spanBuilder("init database")
        .startSpan()
    val db = Database.temporary()
    val schema = MainDatabase()
    span.end()
    db.read { println("Read!") }
    val test = GlobalOpenTelemetry.getMeter("asdf")
        .histogramBuilder("test")
        .build()
    repeat(100) {
        test.record(Random.nextDouble() * 100)
    }
    @WithSpan("db write")
    suspend fun write() {
        db.write {
            schema.create()
            val queries = schema.other
            queries.test()
            println("Wrote!")
        }
    }
    write()
}
