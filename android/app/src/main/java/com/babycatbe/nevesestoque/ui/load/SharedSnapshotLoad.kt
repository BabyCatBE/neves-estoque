package com.babycatbe.nevesestoque.ui.load

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Carga compartilhada de uma tela muito usada (Estoque Atual, Produtos).
 *
 * - [warm] prepara os dados ao abrir o app: primeiro a cópia local do aparelho (instantânea), depois a
 *   leitura oficial em segundo plano.
 * - A tela abre com o último valor disponível ([peek]/[snapshotOrNull]) e pede a leitura oficial com
 *   [load]. Na abertura (`reuseInFlight = true`) reaproveita a leitura que o pré-carregamento já iniciou,
 *   em vez de repetir a mesma consulta. Atualização manual ou após gravação usa `reuseInFlight = false`
 *   e sempre busca dados novos.
 * - A leitura roda num escopo próprio: sair da tela não cancela a carga compartilhada.
 * - Um resultado mais antigo nunca substitui um mais novo.
 *
 * Não altera regra de dados: a leitura oficial continua sendo a mesma do repositório, com o mesmo
 * tratamento de cópia local sem internet.
 */
class SharedSnapshotLoad<T : Any>(
    private val loadOfficial: suspend () -> T,
    private val loadSnapshot: suspend () -> T?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lock = Any()
    private var inFlight: Deferred<T>? = null
    private var generation = 0L
    private var publishedGeneration = 0L
    private val _latest = MutableStateFlow<T?>(null)

    /** Último valor conhecido nesta execução do app (memória). */
    val latest: StateFlow<T?> = _latest.asStateFlow()

    init {
        SharedSnapshotLoads.register(this)
    }

    fun peek(): T? = _latest.value

    /** Valor em memória ou, se ainda não houver, a cópia local do aparelho. Nunca usa rede. */
    suspend fun snapshotOrNull(): T? {
        _latest.value?.let { return it }
        val snapshot = loadSnapshotSafely() ?: return null
        synchronized(lock) {
            if (_latest.value == null) _latest.value = snapshot
        }
        return _latest.value
    }

    /** Leitura oficial. Veja a classe sobre [reuseInFlight]. */
    suspend fun load(reuseInFlight: Boolean): T = request(reuseInFlight).await()

    /** Pré-carregamento: não bloqueia e não mostra erro; a tela trata erros quando abrir. */
    fun warm() {
        scope.launch {
            if (_latest.value == null) snapshotOrNull()
        }
        request(reuseInFlight = true)
    }

    /** Registra um valor oficial obtido por outro caminho (ex.: recarga após salvar ordem). */
    fun remember(value: T) {
        synchronized(lock) {
            val current = ++generation
            publishedGeneration = current
            _latest.value = value
        }
    }

    /** Descarta tudo (troca de usuário, saída da conta, acesso recusado). */
    fun clear() {
        synchronized(lock) {
            inFlight?.cancel()
            inFlight = null
            publishedGeneration = ++generation
            _latest.value = null
        }
    }

    private fun request(reuseInFlight: Boolean): Deferred<T> {
        synchronized(lock) {
            val current = inFlight
            if (reuseInFlight && current != null && current.isActive) return current
            val requestGeneration = ++generation
            val started = scope.async {
                val result = loadOfficial()
                publish(requestGeneration, result)
                result
            }
            inFlight = started
            return started
        }
    }

    private fun publish(requestGeneration: Long, value: T) {
        synchronized(lock) {
            if (requestGeneration >= publishedGeneration) {
                publishedGeneration = requestGeneration
                _latest.value = value
            }
        }
    }

    private suspend fun loadSnapshotSafely(): T? =
        try {
            loadSnapshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
}

/** Registro das cargas compartilhadas, para limpar tudo quando o acesso muda. */
object SharedSnapshotLoads {
    private val loads = mutableListOf<SharedSnapshotLoad<*>>()

    internal fun register(load: SharedSnapshotLoad<*>) {
        synchronized(loads) { loads += load }
    }

    fun clearAll() {
        val current = synchronized(loads) { loads.toList() }
        current.forEach { it.clear() }
    }
}
