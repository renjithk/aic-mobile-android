package edu.artic.map

import android.content.Intent
import android.os.Bundle
import com.bumptech.glide.Glide
import com.bumptech.glide.MemoryCategory
import edu.artic.base.utils.asDeepLinkIntent
import edu.artic.base.utils.disableShiftMode
import edu.artic.base.utils.preventReselection
import edu.artic.db.models.ArticTour
import edu.artic.location.LocationService
import edu.artic.navigation.NavigationConstants
import edu.artic.navigation.NavigationSelectListener
import edu.artic.ui.BaseActivity
import kotlinx.android.synthetic.main.activity_map.*
import javax.inject.Inject

/**
 * One of the four primary sections of the app. This Activity always hosts a [MapFragment].
 *
 * As a primary section, this class always contains a
 * [NarrowAudioPlayerFragment][edu.artic.audioui.NarrowAudioPlayerFragment].
 */
class MapActivity : BaseActivity() {

    @Inject
    lateinit var locationService: LocationService

    override val layoutResId: Int
        get() = R.layout.activity_map

    companion object {
        val ARG_TOUR = "ARG_TOUR"
        val ARG_TOUR_START_STOP = "ARG_TOUR_START_STOP"

        fun launchMapForTour(tour: ArticTour, articTourStop: ArticTour.TourStop): Intent {
            return NavigationConstants.MAP.asDeepLinkIntent().apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtras(Bundle().apply {
                    putParcelable(ARG_TOUR, tour)
                    articTourStop.let { putParcelable(ARG_TOUR_START_STOP, it) }
                })
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bottomNavigation.apply {
            disableShiftMode(R.color.map_menu_color_list)
            selectedItemId = R.id.action_map
            preventReselection()
            setOnNavigationItemSelectedListener(NavigationSelectListener(this.context))
        }
    }

    override fun onStart() {
        super.onStart()
        // The map is resource-intensive. Be kind to it.
        Glide.get(this).setMemoryCategory(MemoryCategory.LOW)
    }

    override fun onStop() {
        super.onStop()
        // The GoogleMap itself will lower its usage at this point, so it is
        // safe to increase the memory category too.
        Glide.get(this).setMemoryCategory(MemoryCategory.NORMAL)
    }

}