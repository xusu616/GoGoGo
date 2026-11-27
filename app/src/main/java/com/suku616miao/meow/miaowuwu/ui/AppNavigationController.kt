package com.suku616miao.meow.miaowuwu.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Build
import com.suku616miao.meow.miaowuwu.logging.AppLogger
import android.view.View
import android.view.ViewGroup
import android.view.RoundedCorner
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
import androidx.drawerlayout.widget.DrawerLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.suku616miao.meow.miaowuwu.MainActivity
import com.suku616miao.meow.miaowuwu.R
import kotlin.random.Random
import kotlin.math.roundToInt

/** One Activity, persistent map underneath a Navigation host for settings/history. */
class AppNavigationController(private val activity: MainActivity) {
    private val drawer: DrawerLayout = activity.findViewById(R.id.drawer_layout)
    private val sidebar: NavigationView = activity.findViewById(R.id.nav_view)
    private val container: MovingPageLayout = activity.findViewById(R.id.nav_page)
    private val pageBackdrop: View = activity.findViewById(R.id.nav_backdrop)
    private val pageViewport: View = activity.findViewById(R.id.nav_viewport)
    private val toolbar: Toolbar = activity.findViewById(R.id.toolbar)
    private val routeButton: View = activity.findViewById(R.id.route_button)
    private val positionButton: View = activity.findViewById(R.id.faBtnStart)
    private val panel: View = activity.findViewById(R.id.route_panel)
    private val mapContent: View = activity.findViewById(R.id.map_content)
    private val contentRoot = drawer.getChildAt(0) as ViewGroup
    private val density = activity.resources.displayMetrics.density
    private var drawerAnimation: SpringAnimation? = null
    private var drawerFade: ValueAnimator? = null
    private var drawerTransition = 0
    private var scrimOpacity = 0f
    private val scrimColor = 0x99000000.toInt()
    private val scrim = ColorDrawable(scrimColor).apply { alpha = 0 }
    private var sidebarExit: MovingPageLayout? = null
    private var contentEntry: SpringAnimation? = null
    private var contentExit: MovingPageLayout? = null
    private var contentExitAnimation: ValueAnimator? = null
    private var contentTransition = 0
    private var backStartX = 0f
    private var backStartY = 0f
    private var backStartScaleX = 1f
    private var backStartScaleY = 1f
    private var buttonAnimation: SpringAnimation? = null
    private var buttonTarget: ButtonTarget? = null
    private var buttonOpeningRequested = false
    private var buttonRotationDirection = 1f
    private var panelFullyHidden = panel.visibility == View.GONE
    private var backStartPanelY = 0f
    private var pageCornerRadii = FloatArray(8)
    private var screenCornersAvailable = false
    private val preferences = UiPreferences.prefs(activity)
    private val cornerPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "permanent_corners" && !inMap && contentEntry == null) {
            readScreenCorners()
            applySettledCorners()
        }
    }
    private val viewSprings = mutableMapOf<Pair<View, DynamicAnimation.ViewProperty>, SpringAnimation>()
    private var drawerClosing = false
    private var destroyed = false
    private var backRegistered = false
    private val host = activity.supportFragmentManager.findFragmentById(R.id.nav_container) as? NavHostFragment
        ?: NavHostFragment.create(R.navigation.app_navigation).also {
            activity.supportFragmentManager.beginTransaction().replace(R.id.nav_container, it)
                .setPrimaryNavigationFragment(it).commitNow()
        }
    private val nav = host.navController
    private var inMap = nav.currentDestination?.id == R.id.map_destination
    private val back = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // The sidebar has one animation owner; never reset it and close it simultaneously.
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                closeDrawer()
            } else {
                when {
                    !inMap -> {
                        startContentExit()
                        nav.popBackStack(R.id.map_destination, false)
                    }
                    activity.isRouteEditorOpen -> activity.toggleRouteEditor()
                    else -> activity.moveTaskToBack(false)
                }
            }
            refreshBackEnabled()
        }

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            drawerTransition++
            drawerFade?.cancel()
            drawerFade = null
            clearSidebarExit()
            drawerAnimation?.cancel()
            drawerClosing = false
            sidebar.alpha = 1f
            setScrimOpacity(if (drawer.isDrawerOpen(GravityCompat.START)) 1f else 0f)
            cancelContentEntry()
            clearContentExit()
            viewSprings.values.forEach { it.cancel() }
            if (inMap && activity.isRouteEditorOpen) {
                buttonAnimation?.cancel()
                buttonAnimation = null
                buttonTarget = null
                backStartPanelY = panel.translationY
            }
            backStartX = container.translationX
            backStartY = container.translationY
            backStartScaleX = container.scaleX
            backStartScaleY = container.scaleY
            container.alpha = 1f
            if (!inMap && !drawer.isDrawerOpen(GravityCompat.START)) pageBackdrop.visibility = View.GONE
            if (!inMap) {
                readScreenCorners()
                roundMovingPage(container)
            }
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            if (!UiPreferences.backEnabled(activity)) return
            val strength = UiPreferences.backStrength(activity)
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                sidebar.translationX = -sidebar.width * backEvent.progress * strength
                setScrimOpacity(1f - backEvent.progress * strength)
            } else if (inMap && activity.isRouteEditorOpen) {
                panel.translationY = backStartPanelY + 64 * density * strength * backEvent.progress
            } else {
                container.pivotX = if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 0f else container.width.toFloat()
                val progress = backEvent.progress
                val targetScale = 1f - 0.08f * strength
                container.scaleX = backStartScaleX + (targetScale - backStartScaleX) * progress
                container.scaleY = backStartScaleY + (targetScale - backStartScaleY) * progress
                val targetX = (if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1) * 24 * density * strength
                container.translationX = backStartX + (targetX - backStartX) * progress
                container.translationY = backStartY * (1f - progress)
            }
        }

        override fun handleOnBackCancelled() {
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                setScrimOpacity(1f)
                animateDrawer(0f) {}
            }
            if (inMap && activity.isRouteEditorOpen) positionRouteButtons(true)
            else resetContentTransform()
        }
    }

    private val drawerListener = object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            if (!drawerClosing) setScrimOpacity(slideOffset)
        }
        override fun onDrawerOpened(drawerView: View) {
            if (!drawerClosing) setScrimOpacity(1f)
            refreshBackEnabled()
        }
        override fun onDrawerClosed(drawerView: View) {
            // A menu selection already closed the logical drawer. Its noninteractive snapshot
            // continues the visual exit independently and must not be cleared by this callback.
            if (drawerClosing) {
                refreshBackEnabled()
                return
            }
            drawerTransition++
            drawerFade?.cancel()
            drawerFade = null
            drawerAnimation?.cancel()
            drawerAnimation = null
            drawerClosing = false
            sidebar.translationX = 0f
            sidebar.alpha = 1f
            setScrimOpacity(0f)
            refreshBackEnabled()
        }
    }
    private val destinationListener = NavController.OnDestinationChangedListener { _, destination, _ ->
        cancelContentEntry()
        cancelContainerSprings()
        inMap = destination.id == R.id.map_destination
        pageBackdrop.visibility = View.GONE
        container.visibility = if (inMap) View.GONE else View.VISIBLE
        pageViewport.visibility = if (!inMap || contentExit != null) View.VISIBLE else View.GONE
        toolbar.title = destination.label
        toolbar.setNavigationIcon(if (inMap) R.drawable.ic_menu_nav else R.drawable.ic_arrow_back)
        routeButton.visibility = if (inMap) View.VISIBLE else View.GONE
        positionButton.visibility = if (inMap) View.VISIBLE else View.GONE
        activity.setMapMenuVisible(inMap)
        UiPreferences.applyViewColors(toolbar)
        refreshBackEnabled()
        if (inMap) resetContainerImmediately() else startContentEntry()
        // Restored navigation can be a settings/history destination while the route is still running.
        if (inMap && activity.isRouteEditorOpen) animateRouteButtons(true)
    }
    private val fragmentColors = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(manager: FragmentManager, fragment: Fragment, view: View, state: Bundle?) {
            UiPreferences.applyViewColors(view)
        }
    }
    private val panelLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (activity.isRouteEditorOpen && inMap) positionRouteButtons(true)
    }
    private val contentLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        scrim.setBounds(0, 0, contentRoot.width, contentRoot.height)
    }

    init {
        val radius = 28 * density
        // Clip the surface that actually moves, including its background and all Fragment children.
        roundMovingPage(container)
        preferences.registerOnSharedPreferenceChangeListener(cornerPreferenceListener)
        sidebar.background = MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(radius).build()).apply {
            fillColor = ColorStateList.valueOf(MaterialColors.getColor(sidebar, com.google.android.material.R.attr.colorSurfaceContainerLow))
        }
        sidebar.clipToOutline = true
        // DrawerLayout multiplies its built-in scrim by its own slide state. A controller-owned
        // overlay stays visible for the full fade even after the logical drawer closes.
        drawer.setScrimColor(Color.TRANSPARENT)
        contentRoot.overlay.add(scrim)
        contentRoot.addOnLayoutChangeListener(contentLayout)
        scrim.setBounds(0, 0, contentRoot.width, contentRoot.height)
        toolbar.setNavigationOnClickListener { if (inMap) toggleDrawer() else back.handleOnBackPressed() }
        drawer.addDrawerListener(drawerListener)
        panel.addOnLayoutChangeListener(panelLayout)
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentColors, true)
        // This listener is called immediately with the restored destination, not only after new navigation.
        nav.addOnDestinationChangedListener(destinationListener)
        UiPreferences.applyViewColors(activity.findViewById(android.R.id.content))
    }

    fun onResume() {
        if (!backRegistered) {
            activity.onBackPressedDispatcher.addCallback(activity, back)
            backRegistered = true
        }
        refreshBackEnabled()
    }

    fun navigate(id: Int) {
        closeDrawer()
        if (nav.currentDestination?.id == id) return
        if (inMap) clearContentExit() else startContentExit()
        if (id == R.id.map_destination) nav.popBackStack(id, false)
        else if (nav.currentDestination?.id != id) nav.navigate(id, null,
            NavOptions.Builder().setPopUpTo(R.id.map_destination, false).setLaunchSingleTop(true).build())
    }

    private fun refreshBackEnabled() {
        back.isEnabled = drawer.isDrawerOpen(GravityCompat.START) || !inMap || activity.isRouteEditorOpen
    }

    fun toggleDrawer() {
        if (drawer.isDrawerOpen(GravityCompat.START) && !drawerClosing) closeDrawer() else {
            val transition = ++drawerTransition
            drawerFade?.cancel()
            drawerFade = null
            val exitingX = sidebarExit?.translationX
            clearSidebarExit()
            drawerAnimation?.cancel()
            drawerClosing = false
            val alreadyOpen = drawer.isDrawerOpen(GravityCompat.START)
            sidebar.alpha = 1f
            setScrimOpacity(1f)
            // Set the offscreen position before openDrawer can expose the fully laid-out drawer.
            // The pre-draw callback covers the first opening when width is not measured yet.
            if (!alreadyOpen && UiPreferences.springEnabled(activity)) sidebar.translationX = exitingX ?: -sidebar.width.toFloat()
            drawer.openDrawer(sidebar, false)
            sidebar.doOnPreDraw {
                if (!destroyed && transition == drawerTransition && !drawerClosing) {
                    if (!alreadyOpen && exitingX == null && UiPreferences.springEnabled(activity)) sidebar.translationX = -sidebar.width.toFloat()
                    animateDrawer(0f) {}
                }
            }
            refreshBackEnabled()
        }
    }

    fun closeDrawer() {
        if (!drawer.isDrawerOpen(GravityCompat.START) || drawerClosing) return
        val transition = ++drawerTransition
        drawerClosing = true
        drawerFade?.cancel()
        drawerAnimation?.cancel()
        val exitingSidebar = createSidebarExit()
        val startOpacity = scrimOpacity
        val startAlpha = exitingSidebar.alpha
        // Overlay views do not receive touch events. Release the logical modal drawer immediately,
        // then let its snapshot slide and fade while the destination remains directly usable.
        drawer.closeDrawer(sidebar, false)
        sidebar.translationX = 0f
        sidebar.alpha = 1f
        refreshBackEnabled()
        animateDrawer(-sidebar.width.toFloat(), exitingSidebar) {}
        drawerFade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener { animator ->
                val remaining = 1f - (animator.animatedValue as Float)
                setScrimOpacity(startOpacity * remaining)
                exitingSidebar.alpha = startAlpha * remaining
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (destroyed || transition != drawerTransition || !drawerClosing) return
                    drawerFade = null
                    drawerAnimation?.cancel()
                    drawerAnimation = null
                    clearSidebarExit()
                    drawerClosing = false
                    setScrimOpacity(0f)
                    refreshBackEnabled()
                }
            })
            start()
        }
    }

    private fun setScrimOpacity(opacity: Float) {
        scrimOpacity = opacity
        scrim.alpha = (Color.alpha(scrimColor) * opacity).roundToInt()
    }

    private fun createSidebarExit(): MovingPageLayout {
        clearSidebarExit()
        val bitmap = sidebar.drawToBitmap()
        return MovingPageLayout(activity).apply {
            addView(ImageView(activity).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_XY
            }, FrameLayout.LayoutParams(-1, -1))
            // Sidebar design radius remains separate from the device-derived page corners.
            setCornerRadii(FloatArray(8) { 28 * density })
            measure(View.MeasureSpec.makeMeasureSpec(sidebar.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(sidebar.height, View.MeasureSpec.EXACTLY))
            layout(sidebar.left, sidebar.top, sidebar.right, sidebar.bottom)
            translationX = sidebar.translationX
            alpha = sidebar.alpha
            drawer.overlay.add(this)
            sidebarExit = this
        }
    }

    private fun clearSidebarExit() {
        sidebarExit?.let {
            drawer.overlay.remove(it)
            it.removeAllViews()
        }
        sidebarExit = null
    }

    private fun roundMovingPage(page: MovingPageLayout) {
        page.setCornerRadii(pageCornerRadii)
    }

    private fun readScreenCorners() {
        pageCornerRadii = FloatArray(8)
        screenCornersAvailable = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val positions = listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT)
        val corners = positions.map { position ->
            container.display?.getRoundedCorner(position) ?: container.rootWindowInsets?.getRoundedCorner(position)
        }
        if (corners.all { it == null }) {
            AppLogger.i("GoPageCorners", "Android did not provide screen corners; using filled page without an invented radius")
            return
        }
        val topRadii = corners.flatMap { corner ->
            val radius = corner?.radius?.toFloat() ?: 0f
            listOf(radius, radius)
        }.toFloatArray()
        topRadii.copyInto(pageCornerRadii)
        screenCornersAvailable = true
        AppLogger.i("GoPageCorners", "Android top corner radii in px: ${topRadii.filterIndexed { index, _ -> index % 2 == 0 }}")
    }

    private fun applySettledCorners() {
        container.setCornerRadii(if (UiPreferences.permanentCorners(activity)) pageCornerRadii else FloatArray(8))
        if (!inMap) pageBackdrop.visibility = if (screenCornersAvailable) View.GONE else View.VISIBLE
    }

    private fun clearContentExit() {
        contentExitAnimation?.cancel()
        contentExitAnimation = null
        contentExit?.let { page ->
            (container.parent as ViewGroup).overlay.remove(page)
            page.removeAllViews()
        }
        contentExit = null
        if (inMap) pageViewport.visibility = View.GONE
    }

    private fun startContentExit() {
        clearContentExit()
        cancelContentEntry()
        cancelContainerSprings()
        pageBackdrop.visibility = View.GONE
        // A return before the first layout has no visible page to animate.
        if (!container.isLaidOut) return
        readScreenCorners()
        roundMovingPage(container)
        val parent = container.parent as ViewGroup
        val page = MovingPageLayout(activity).apply {
            addView(ImageView(activity).apply {
                setImageBitmap(container.drawToBitmap())
                scaleType = ImageView.ScaleType.FIT_XY
            }, FrameLayout.LayoutParams(-1, -1))
            measure(View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.EXACTLY))
            layout(container.left, container.top, container.right, container.bottom)
            translationX = container.translationX
            translationY = container.translationY
            pivotX = container.pivotX
            pivotY = container.pivotY
            scaleX = container.scaleX
            scaleY = container.scaleY
            alpha = container.alpha
            // drawToBitmap does not preserve the hardware outline; the moving snapshot also clips.
            roundMovingPage(this)
        }
        parent.overlay.add(page)
        contentExit = page
        val startX = page.translationX
        val startY = page.translationY
        val startAlpha = page.alpha
        contentExitAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                page.translationX = startX + (page.width - startX) * progress
                page.translationY = startY + (page.height - startY) * progress
                page.alpha = startAlpha * (1f - progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Cancellation can be followed immediately by a different navigation.
                    if (contentExit === page) {
                        parent.overlay.remove(page)
                        page.removeAllViews()
                        contentExit = null
                        contentExitAnimation = null
                        if (inMap) pageViewport.visibility = View.GONE
                    }
                }
            })
            start()
        }
    }

    private fun animateDrawer(target: Float, targetView: View = sidebar, finished: () -> Unit) {
        drawerAnimation?.cancel()
        if (!UiPreferences.springEnabled(activity)) {
            targetView.translationX = target
            finished()
            return
        }
        drawerAnimation = SpringAnimation(targetView, DynamicAnimation.TRANSLATION_X, target).apply {
            spring.stiffness = UiPreferences.stiffness(activity)
            spring.dampingRatio = UiPreferences.damping(activity)
            addEndListener { _, cancelled, _, _ -> if (!cancelled && !destroyed) finished() }
            start()
        }
    }

    private fun cancelContentEntry() {
        contentTransition++
        contentEntry?.cancel()
        contentEntry = null
    }

    private fun cancelContainerSprings() {
        val entries = viewSprings.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            if (entry.key.first === container) {
                entry.value.cancel()
                entries.remove()
            }
        }
    }

    private fun resetContainerImmediately() {
        container.translationX = 0f
        container.translationY = 0f
        container.scaleX = 1f
        container.scaleY = 1f
        container.alpha = 1f
        applySettledCorners()
    }

    private fun startContentEntry() {
        pageBackdrop.visibility = View.GONE
        val transition = ++contentTransition
        if (!UiPreferences.springEnabled(activity)) {
            resetContainerImmediately()
            container.doOnPreDraw {
                if (!destroyed && transition == contentTransition && !inMap) {
                    readScreenCorners()
                    applySettledCorners()
                }
            }
            return
        }
        container.translationX = 0f
        container.scaleX = 1f
        container.scaleY = 1f
        container.alpha = 0f
        container.doOnPreDraw {
            if (destroyed || transition != contentTransition || inMap) return@doOnPreDraw
            val startY = container.height.toFloat()
            readScreenCorners()
            roundMovingPage(container)
            container.translationY = startY
            // One progress value owns every entry transform. Back gestures cancel it before writing.
            contentEntry = SpringAnimation(FloatValueHolder(0f)).apply {
                spring = SpringForce(1f).setStiffness(UiPreferences.stiffness(activity))
                    .setDampingRatio(UiPreferences.damping(activity))
                minimumVisibleChange = 0.001f
                addUpdateListener { _, progress, _ ->
                    container.translationY = startY * (1f - progress)
                    // A spring can overshoot; opacity is confined to its valid rendering range.
                    container.alpha = progress.coerceIn(0f, 1f)
                }
                addEndListener { animation, cancelled, _, _ ->
                    if (!cancelled && !destroyed && transition == contentTransition) resetContainerImmediately()
                    if (contentEntry === animation) contentEntry = null
                }
                start()
            }
        }
    }

    fun animateRouteButtons(open: Boolean) {
        if (open != buttonOpeningRequested) {
            buttonOpeningRequested = open
            if (open) buttonRotationDirection = if (Random.nextBoolean()) 1f else -1f
        }
        refreshBackEnabled()
        if (!open) {
            positionRouteButtons(false)
            return
        }
        // setVisibility(VISIBLE) only requests layout. Use the measured panel top after that layout.
        panel.doOnPreDraw {
            if (!destroyed && activity.isRouteEditorOpen) positionRouteButtons(true)
        }
    }

    private fun positionRouteButtons(open: Boolean) {
        val panelOffset = if (open) 0f else panel.height + 16 * density
        if (open && panelFullyHidden) {
            panelFullyHidden = false
            panel.translationY = panel.height + 16 * density
        } else if (!open && panelFullyHidden) {
            panel.visibility = View.GONE
        }
        val gap = 12 * density
        val positionOffset = if (open) panel.top - gap - positionButton.bottom else 0f
        val routeOffset = if (open) panel.top - gap - routeButton.bottom else 0f
        val rotation = if (open) 180f * buttonRotationDirection else 0f
        animateButtonsTogether(ButtonTarget(positionOffset, routeOffset, rotation, panelOffset, open))
        val parameters = mapContent.layoutParams as FrameLayout.LayoutParams
        val bottomMargin = if (open) panel.height + (16 * density).toInt() else 0
        if (parameters.bottomMargin != bottomMargin) {
            parameters.bottomMargin = bottomMargin
            mapContent.layoutParams = parameters
        }
    }

    private data class ButtonTarget(val positionOffset: Float, val routeOffset: Float,
        val rotation: Float, val panelOffset: Float, val open: Boolean)

    private fun animateButtonsTogether(target: ButtonTarget) {
        // Layout callbacks often repeat the same measurements. Keep the active timeline intact.
        if (buttonTarget == target) return
        buttonTarget = target
        buttonAnimation?.cancel()
        buttonAnimation = null

        val positionStart = positionButton.translationY
        val routeStart = routeButton.translationY
        val positionRotationStart = positionButton.rotation
        val routeRotationStart = routeButton.rotation
        val panelStart = panel.translationY

        fun applyProgress(progress: Float) {
            positionButton.translationY = positionStart + (target.positionOffset - positionStart) * progress
            routeButton.translationY = routeStart + (target.routeOffset - routeStart) * progress
            positionButton.rotation = positionRotationStart + (target.rotation - positionRotationStart) * progress
            routeButton.rotation = routeRotationStart + (target.rotation - routeRotationStart) * progress
            panel.translationY = panelStart + (target.panelOffset - panelStart) * progress
        }

        fun finish() {
            applyProgress(1f)
            if (!target.open) {
                panelFullyHidden = true
                panel.visibility = View.GONE
                panel.translationY = 0f
            }
        }

        if (!UiPreferences.springEnabled(activity)) {
            finish()
            return
        }

        // One spring drives all four properties, so raising/lowering and rotation have identical
        // timing. Retargeting captures the currently displayed properties without snapping.
        buttonAnimation = SpringAnimation(FloatValueHolder(0f)).apply {
            spring = SpringForce(1f).setStiffness(UiPreferences.stiffness(activity))
                .setDampingRatio(UiPreferences.damping(activity))
            minimumVisibleChange = 0.001f
            addUpdateListener { _, progress, _ -> applyProgress(progress) }
            addEndListener { animation, cancelled, _, _ ->
                if (!cancelled && !destroyed) finish()
                if (buttonAnimation === animation) buttonAnimation = null
            }
            start()
        }
    }

    private fun spring(view: View, property: DynamicAnimation.ViewProperty, target: Float) {
        val key = view to property
        if (!UiPreferences.springEnabled(activity)) {
            viewSprings.remove(key)?.cancel()
            property.setValue(view, target)
            showBackdropIfSettled()
            return
        }
        val animation = viewSprings.getOrPut(key) {
            SpringAnimation(view, property).apply {
                addEndListener { _, cancelled, _, _ -> if (!cancelled && !destroyed) showBackdropIfSettled() }
            }
        }
        animation.spring = SpringForce(target).setStiffness(UiPreferences.stiffness(activity)).setDampingRatio(UiPreferences.damping(activity))
        animation.animateToFinalPosition(target)
    }

    private fun resetContentTransform() {
        cancelContentEntry()
        container.alpha = 1f
        spring(container, DynamicAnimation.SCALE_X, 1f)
        spring(container, DynamicAnimation.SCALE_Y, 1f)
        spring(container, DynamicAnimation.TRANSLATION_X, 0f)
        spring(container, DynamicAnimation.TRANSLATION_Y, 0f)
        showBackdropIfSettled()
    }

    private fun showBackdropIfSettled() {
        if (!inMap && container.translationX == 0f && container.translationY == 0f &&
            container.scaleX == 1f && container.scaleY == 1f) {
            applySettledCorners()
        }
    }

    fun destroy() {
        destroyed = true
        back.remove()
        drawer.removeDrawerListener(drawerListener)
        contentRoot.removeOnLayoutChangeListener(contentLayout)
        contentRoot.overlay.remove(scrim)
        panel.removeOnLayoutChangeListener(panelLayout)
        nav.removeOnDestinationChangedListener(destinationListener)
        activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentColors)
        preferences.unregisterOnSharedPreferenceChangeListener(cornerPreferenceListener)
        drawerAnimation?.cancel()
        drawerFade?.cancel()
        clearSidebarExit()
        clearContentExit()
        cancelContentEntry()
        buttonAnimation?.cancel()
        viewSprings.values.forEach { it.cancel() }
        viewSprings.clear()
    }
}

class MapDestinationFragment : Fragment()
