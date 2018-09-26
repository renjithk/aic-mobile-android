package edu.artic.events

import com.fuzz.rx.bindTo
import com.fuzz.rx.disposedBy
import edu.artic.analytics.AnalyticsAction
import edu.artic.analytics.AnalyticsTracker
import edu.artic.analytics.ScreenCategoryName
import edu.artic.base.utils.DateTimeHelper
import edu.artic.db.models.ArticEvent
import edu.artic.localization.LanguageSelector
import edu.artic.viewmodel.NavViewViewModel
import edu.artic.viewmodel.Navigate
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class EventDetailViewModel @Inject constructor(val analyticsTracker: AnalyticsTracker,
                                               languageSelector: LanguageSelector)
    : NavViewViewModel<EventDetailViewModel.NavigationEndpoint>() {

    sealed class NavigationEndpoint {
        class LoadUrl(val url: String) : NavigationEndpoint()
    }

    val imageUrl: Subject<String> = BehaviorSubject.create()
    val title: Subject<String> = BehaviorSubject.createDefault("test")
    val metaData: Subject<String> = BehaviorSubject.createDefault("")
    val description: Subject<String> = BehaviorSubject.createDefault("")
    val throughDate: Subject<String> = BehaviorSubject.createDefault("")
    val location: Subject<String> = BehaviorSubject.createDefault("")
    val eventButtonText: Subject<String> = BehaviorSubject.createDefault("")
    private val eventObservable: Subject<ArticEvent> = BehaviorSubject.create()


    var event: ArticEvent? = null
        set(value) {
            field = value
            value?.let {
                eventObservable.onNext(it)
            }
        }

    init {

        eventObservable
                .map {
                    it.button_text.orEmpty()
                }
                .bindTo(eventButtonText)
                .disposedBy(disposeBag)

        eventObservable
                .map { it.title }
                .bindTo(title)
                .disposedBy(disposeBag)

        eventObservable
                .map { it.imageURL }
                .bindTo(imageUrl)
                .disposedBy(disposeBag)

        eventObservable
                .map { it.startTime.format(DateTimeHelper.HOME_EVENT_DATE_FORMATTER) }
                .bindTo(metaData)
                .disposedBy(disposeBag)

        eventObservable
                .filter { it.description != null }
                .map { it.description!! }
                .bindTo(description)
                .disposedBy(disposeBag)

        eventObservable
                .map { event ->
                    val formatter = DateTimeHelper.HOME_EXHIBITION_DATE_FORMATTER
                            .withLocale(languageSelector.getAppLocale())
                    event.endTime.format(formatter)
                }
                .bindTo(throughDate)
                .disposedBy(disposeBag)

        /**
         * Listen for language changes.
         */
        languageSelector.currentLanguage
                .withLatestFrom(eventObservable)
                .map { (locale, event) ->
                    val formatter = DateTimeHelper.HOME_EXHIBITION_DATE_FORMATTER
                            .withLocale(locale)
                    event.endTime.format(formatter)
                }
                .bindTo(throughDate)
                .disposedBy(disposeBag)

        eventObservable
                .filter { it.location != null }
                .map { it.location!! }
                .bindTo(location)
                .disposedBy(disposeBag)
    }

    fun onClickRegisterToday() {
        event?.let {
            analyticsTracker.reportEvent(ScreenCategoryName.Events, AnalyticsAction.linkPressed, it.title)
            navigateTo.onNext(Navigate.Forward(NavigationEndpoint.LoadUrl(it.buttonURL)))
        }
    }
}