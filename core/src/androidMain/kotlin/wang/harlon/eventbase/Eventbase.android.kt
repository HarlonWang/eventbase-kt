package wang.harlon.eventbase

import android.content.Context
import io.ktor.client.HttpClient

fun Eventbase.init(
    context: Context,
    config: EventbaseConfig,
    httpClient: HttpClient = HttpClient(),
): EventbaseClient = init(config, SharedPrefsStorage(context), httpClient)
