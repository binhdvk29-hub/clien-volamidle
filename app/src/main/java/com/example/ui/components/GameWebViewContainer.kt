package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import com.example.model.TabLimits
import com.example.model.ViewLayoutMode
import com.example.webview.GameTabManager

class TabCardViewHolder(
    val root: LinearLayout,
    val idleOverlay: LinearLayout,
    val idleOpenBtn: Button,
    val idleTitle: TextView,
    var onSelectTab: (Int) -> Unit
)

@Composable
fun GameWebViewContainer(
    tabManager: GameTabManager,
    tabCount: Int,
    characterStatsList: List<CharacterStats>,
    selectedTabIndex: Int,
    layoutMode: ViewLayoutMode,
    isDownloading: Boolean,
    onSelectTab: (Int) -> Unit,
    onOpenControlSheet: (Int) -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    tabManager.updateActivityContext(context)

    val holders = remember { HashMap<Int, TabCardViewHolder>() }

    fun holderFor(index: Int): TabCardViewHolder = holders.getOrPut(index) {
        createTabCardViewHolder(context, index, tabManager)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
            .testTag("webview_game_container")
    ) {
        AndroidView(
            factory = { ctx ->
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.parseColor("#090D14"))
                }
            },
            update = { rootLayout ->
                val safeCount = tabCount.coerceIn(0, TabLimits.SLOT_COUNT)
                val keySelected = if (layoutMode == ViewLayoutMode.SINGLE_TAB) {
                    selectedTabIndex.coerceIn(0, (safeCount - 1).coerceAtLeast(0))
                } else {
                    -1
                }
                val currentLayoutKey = if (layoutMode == ViewLayoutMode.SINGLE_TAB) {
                    "SINGLE_TAB:$safeCount"
                } else {
                    "${layoutMode.name}:${safeCount}:${keySelected}"
                }
                val lastLayoutKey = rootLayout.tag as? String

                val visibleIndices: List<Int> = if (layoutMode == ViewLayoutMode.SINGLE_TAB) {
                    if (safeCount == 0) emptyList() else listOf(keySelected)
                } else {
                    (0 until safeCount).toList()
                }
                tabManager.updateVisibleTabs(visibleIndices.toSet())

                val allTabIndices = (0 until safeCount).toList()
                for (index in allTabIndices) {
                    holderFor(index).onSelectTab = onSelectTab
                }

                if (lastLayoutKey != currentLayoutKey || rootLayout.childCount == 0) {
                    rootLayout.tag = currentLayoutKey

                    for (holder in holders.values) {
                        (holder.root.parent as? ViewGroup)?.removeView(holder.root)
                    }
                    rootLayout.removeAllViews()

                    val plan = TabLayoutPlan.planFor(layoutMode, safeCount)

                    when (layoutMode) {
                        ViewLayoutMode.FOUR_COLUMNS -> {
                            if (plan.useLegacyRow) {
                                rootLayout.orientation = LinearLayout.HORIZONTAL
                                rootLayout.weightSum = safeCount.toFloat()

                                for (i in 0 until safeCount) {
                                    val holder = holderFor(i)
                                    holder.root.alpha = 1f
                                    holder.root.translationZ = 0f
                                    holder.root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                    holder.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                    holder.root.isClickable = false
                                    val params = LinearLayout.LayoutParams(
                                        0,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        1f
                                    ).apply {
                                        if (i > 0) leftMargin = TabLayoutPlan.GAP_PX
                                    }
                                    holder.root.layoutParams = params
                                    rootLayout.addView(holder.root)
                                }
                            } else {
                                for (i in 0 until safeCount) {
                                    val holder = holderFor(i)
                                    holder.root.alpha = 1f
                                    holder.root.translationZ = 0f
                                    holder.root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                    holder.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                    holder.root.isClickable = false
                                }
                                buildGrid(rootLayout, safeCount, plan.cols, plan.rows) { index ->
                                    holderFor(index)
                                }
                            }
                        }

                        ViewLayoutMode.TWO_BY_TWO -> {
                            for (i in 0 until safeCount) {
                                val holder = holderFor(i)
                                holder.root.alpha = 1f
                                holder.root.translationZ = 0f
                                holder.root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                holder.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                holder.root.isClickable = false
                            }
                            buildGrid(rootLayout, safeCount, plan.cols, plan.rows) { index ->
                                holderFor(index)
                            }
                        }

                        ViewLayoutMode.SINGLE_TAB -> {
                            rootLayout.orientation = LinearLayout.VERTICAL
                            rootLayout.weightSum = 1f
                            val stack = FrameLayout(rootLayout.context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f
                                )
                                setBackgroundColor(AndroidColor.parseColor("#090D14"))
                            }
                            for (i in 0 until safeCount) {
                                val holder = holderFor(i)
                                holder.root.layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                holder.root.visibility = View.VISIBLE
                                stack.addView(holder.root)
                            }
                            rootLayout.addView(stack)
                        }
                    }

                    tabManager.adjustAllViewports()
                }

                if (layoutMode == ViewLayoutMode.SINGLE_TAB && safeCount > 0) {
                    for (i in 0 until safeCount) {
                        val holder = holderFor(i)
                        val isSelected = (i == keySelected)
                        holder.root.visibility = View.VISIBLE
                        // Tối ưu GPU điện thoại: giảm overdraw cho tab nền nhưng vẫn giữ kết nối WebSocket sống
                        holder.root.alpha = if (isSelected) 1f else 0.01f
                        holder.root.isClickable = isSelected
                        holder.root.descendantFocusability = if (isSelected) {
                            ViewGroup.FOCUS_AFTER_DESCENDANTS
                        } else {
                            ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        }
                        holder.root.importantForAccessibility = if (isSelected) {
                            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        } else {
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        }
                        holder.root.translationZ = if (isSelected) 4f else 0f
                    }
                    holderFor(keySelected).root.bringToFront()
                }

                for (i in allTabIndices) {
                    val holder = holderFor(i)
                    val stats = characterStatsList.getOrElse(i) { CharacterStats(tabIndex = i) }

                    if (stats.state == TabLifecycleState.IDLE || stats.state == TabLifecycleState.OFFLINE) {
                        if (holder.idleOverlay.visibility != View.VISIBLE) {
                            holder.idleOverlay.visibility = View.VISIBLE
                        }
                        val desiredIdleText = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) {
                            "Tab ${i + 1}\nChờ mở"
                        } else {
                            "Tab ${i + 1}: Chờ mở tab (Idle)"
                        }
                        if (holder.idleTitle.text != desiredIdleText) {
                            holder.idleTitle.text = desiredIdleText
                        }
                        val desiredBtnText = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) {
                            "▶ Mở"
                        } else {
                            "▶ MỞ TAB ${i + 1}"
                        }
                        if (holder.idleOpenBtn.text != desiredBtnText) {
                            holder.idleOpenBtn.text = desiredBtnText
                        }
                    } else {
                        if (holder.idleOverlay.visibility != View.GONE) {
                            holder.idleOverlay.visibility = View.GONE
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        GameStatusStrip(
            tabManager = tabManager,
            tabCount = tabCount,
            characterStatsList = characterStatsList,
            selectedTabIndex = selectedTabIndex,
            isDownloading = isDownloading,
            onSelectTab = onSelectTab,
            onOpenSidebar = onOpenSidebar,
            onOpenControlSheet = onOpenControlSheet
        )
    }
}

private fun buildGrid(
    root: LinearLayout,
    count: Int,
    cols: Int,
    rows: Int,
    holderFor: (Int) -> TabCardViewHolder
) {
    root.orientation = LinearLayout.VERTICAL
    root.weightSum = rows.toFloat()

    for (r in 0 until rows) {
        val row = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = cols.toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                if (r > 0) topMargin = TabLayoutPlan.GAP_PX
            }
        }

        val start = r * cols
        val end = minOf(count - 1, (r + 1) * cols - 1)
        for (i in start..end) {
            val holder = holderFor(i)
            val columnInRow = i - start
            val params = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                if (columnInRow > 0) leftMargin = TabLayoutPlan.GAP_PX
            }
            holder.root.layoutParams = params
            row.addView(holder.root)
        }

        root.addView(row)
    }
}

private fun createTabCardViewHolder(
    context: Context,
    tabIndex: Int,
    tabManager: GameTabManager
): TabCardViewHolder {
    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AndroidColor.parseColor("#0F172A"))
    }

    val density = context.resources.displayMetrics.density

    val contentContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        setBackgroundColor(AndroidColor.parseColor("#090D14"))
    }

    val webView = tabManager.getOrCreateWebView(tabIndex)
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    contentContainer.addView(webView)

    var attachedWebView: WebView = webView
    tabManager.registerSlot(tabIndex) { newWebView ->
        if (newWebView !== attachedWebView) {
            (attachedWebView.parent as? ViewGroup)?.removeView(attachedWebView)
            attachedWebView = newWebView
        }
        (newWebView.parent as? ViewGroup)?.removeView(newWebView)
        contentContainer.addView(
            newWebView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    val idleOverlay = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val pad = (4 * density).toInt()
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(AndroidColor.parseColor("#0B0F19"))
    }

    val idleTitle = TextView(context).apply {
        gravity = Gravity.CENTER
        text = "Tab ${tabIndex + 1}: Chờ mở tab (Idle)"
        setTextColor(AndroidColor.parseColor("#94A3B8"))
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
    }
    idleOverlay.addView(idleTitle)

    var holderRef: TabCardViewHolder? = null

    val idleOpenBtn = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (32 * density).toInt()
        ).apply {
            topMargin = (8 * density).toInt()
        }
        val btnPadH = (8 * density).toInt()
        setPadding(btnPadH, 0, btnPadH, 0)
        minWidth = (48 * density).toInt()
        minimumWidth = (48 * density).toInt()
        text = "▶ MỞ TAB ${tabIndex + 1}"
        textSize = 9.5f
        isSingleLine = true
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.parseColor("#15803D"))
        setOnClickListener {
            holderRef?.onSelectTab?.invoke(tabIndex)
            tabManager.openSingleTab(tabIndex)
        }
    }
    idleOverlay.addView(idleOpenBtn)

    contentContainer.addView(idleOverlay)
    card.addView(contentContainer)

    return TabCardViewHolder(
        root = card,
        idleOverlay = idleOverlay,
        idleOpenBtn = idleOpenBtn,
        idleTitle = idleTitle,
        onSelectTab = {}
    ).also { holderRef = it }
}
