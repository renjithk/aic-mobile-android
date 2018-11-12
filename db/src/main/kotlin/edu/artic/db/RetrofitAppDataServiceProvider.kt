package edu.artic.db

import com.fuzz.retrofit.rx.requireValue
import com.fuzz.rx.bindTo
import com.jobinlawrance.downloadprogressinterceptor.ProgressEventBus
import edu.artic.db.daos.ArticDataObjectDao
import edu.artic.db.models.ArticDataObject
import edu.artic.getErrorMessage
import edu.artic.throwIfError
import io.reactivex.Observable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import retrofit2.Retrofit
import timber.log.Timber

/**
 * Reference implementation of [AppDataServiceProvider] for the Art Institute of Chicago.
 *
 * The [Retrofit] parameter here is expected (but not required) to be one generated by
 * the inline function '[constructRetrofit]'. All of the [Observable]s used in this file
 * are expected to run in the [io.reactivex.schedulers.Schedulers.io] thread-pool.
 */
class RetrofitAppDataServiceProvider(
        retrofit: Retrofit,
        private val progressEventBus: ProgressEventBus,
        dataObjectDao: ArticDataObjectDao
) : AppDataServiceProvider {
    companion object {
        const val BLOB_HEADER_ID = "blob_download_header_id"
        const val EXHIBITIONS_HEADER_ID = "exhibitions_download_header_id"
        const val EVENT_HEADER_ID = "events_download_header_id"
    }

    val dataObject: Subject<ArticDataObject> = BehaviorSubject.create()

    init {
        dataObjectDao
                .getDataObject()
                .observeOn(Schedulers.io())
                .bindTo(dataObject)
    }

    private val service = retrofit.create(AppDataApi::class.java)

    override fun getBlob(): Observable<ProgressDataState> {
        return Observable.create { observer ->
            val disposable = progressEventBus.observable()
                    .subscribe {
                        if (it.downloadIdentifier == BLOB_HEADER_ID) {
                            observer.onNext(ProgressDataState.Downloading(it.progress / 100f))
                        }
                    }
            service.getBlob(BLOB_HEADER_ID)
                    .subscribe({
                        if (it.isError) {
                            observer.onError(it.error())
                            it.error().printStackTrace()
                        } else {
                            observer.onNext(ProgressDataState.Done(it.requireValue(), it.response().headers().toMultimap()))
                        }
                        disposable.dispose()
                    }, {
                        observer.onError(it)
                        disposable.dispose()
                    }, {
                        observer.onComplete()
                        disposable.dispose()
                    })
        }
    }

    override fun getBlobHeaders(): Observable<Map<String, List<String>>> {
        return Observable.create { observer ->
            service.getBlobHeaders()
                    .subscribe({
                        observer.onNext(it.headers().toMultimap())
                    }, {
                        observer.onError(it)
                    }, {
                        observer.onComplete()
                    })

        }
    }

    override fun getExhibitions(): Observable<ProgressDataState> {
        return Observable.create { observer ->
            dataObject.subscribeBy(
                    onError = { observer.onError(it) },
                    onNext = { dataObject ->
                        var url = dataObject.dataApiUrl + dataObject.exhibitionsEndpoint
                        if (!url.contains("/search")) {
                            url += "/search"
                        }
                        url += "?limit=99"

                        val postParams = ApiBodyGenerator.createExhibitionQueryBody()

                        service.getExhibitions(EXHIBITIONS_HEADER_ID, url, postParams)
                                .map { it.throwIfError() }
                                .subscribe({
                                    if (it.response().isSuccessful) {
                                        observer.onNext(
                                                ProgressDataState.Done(
                                                        it.requireValue(),
                                                        it.response().headers().toMultimap()
                                                )
                                        )
                                    } else {
                                        val errorMessage: String? = it.getErrorMessage()
                                        val error = Throwable(errorMessage, it.error())
                                        Timber.e(error)
                                        observer.onError(error)
                                    }

                                }, {
                                    observer.onError(it)

                                }, {
                                    observer.onComplete()

                                }

                                )
                    })
        }

    }


    override fun getEvents(): Observable<ProgressDataState> {
        return Observable.create { observer ->
            dataObject.subscribeBy(
                    onError = { observer.onError(it) },
                    onNext = { dataObject ->
                        var url = dataObject.dataApiUrl + dataObject.eventsEndpoint
                        if (!url.contains("/search")) {
                            url += "/search"
                        }
                        url += "?limit=500"

                        val postParams = ApiBodyGenerator.createEventQueryBody()

                        service.getEvents(EVENT_HEADER_ID, url, postParams)
                                .map { it.throwIfError() }
                                .subscribe({
                                    if (it.response().isSuccessful) {
                                        observer.onNext(
                                                ProgressDataState.Done(
                                                        it.requireValue(),
                                                        it.response().headers().toMultimap()
                                                )
                                        )
                                    } else {
                                        val errorMessage: String? = it.getErrorMessage()
                                        val error = Throwable(errorMessage, it.error())
                                        Timber.e(error)
                                        observer.onError(error)
                                    }
                                }, {
                                    observer.onError(it)
                                }, {
                                    observer.onComplete()
                                }

                                )
                    })
        }
    }

}