package edu.artic.map

import com.fuzz.rx.Optional
import edu.artic.db.models.ArticObject
import edu.artic.db.models.ArticTour
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject


/**
 * Responsible for managing the communication between search details and the map.
 * @author Sameer Dhakal (Fuzz)
 */
class SearchManager {
    /**
     * Selected ArticObject
     */
    val selectedObject: Subject<Optional<ArticObject>> = BehaviorSubject.createDefault(Optional(null))
    /**
     * Save the last selected tour.
     */
    val selectedAmenityType: Subject<Optional<ArticObject>> = BehaviorSubject.createDefault(Optional(null))

    val leaveSearchMode: Subject<Boolean> = PublishSubject.create()
}