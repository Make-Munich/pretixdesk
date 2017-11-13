package eu.pretix.pretixdesk.ui

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXDialog
import eu.pretix.libpretixsync.check.TicketCheckProvider
import eu.pretix.pretixdesk.PretixDeskMain
import eu.pretix.pretixdesk.ui.helpers.*
import eu.pretix.pretixdesk.ui.style.MainStyleSheet
import eu.pretix.pretixdesk.ui.style.STYLE_BACKGROUND_COLOR
import eu.pretix.pretixdesk.ui.style.STYLE_STATE_VALID_COLOR
import javafx.animation.Timeline
import javafx.geometry.Pos
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.layout.VBox
import javafx.util.Duration
import tornadofx.*

class MainView : View() {
    private val controller: MainController by inject()
    private var resultCards: List<VBox> = ArrayList()
    private var spinnerAnimation: Timeline? = null
    private var syncStatusTimeline: Timeline? = null
    private var syncTriggerTimeline: Timeline? = null

    private val searchField = textfield {
        promptText = "Ticket code or name…"
        addClass(MainStyleSheet.mainSearchField)

        setOnKeyReleased {
            if (it.code == KeyCode.ENTER) {
                handleInput()
            }
        }
    }

    private val mainSpinner = jfxSpinner {
        useMaxHeight = false
        useMaxWidth = false
        opacity = 0.0
    }

    private val resultHolder = stackpane {
        addClass(eu.pretix.pretixdesk.ui.style.MainStyleSheet.resultHolder)

        vbox {
            this += mainSpinner
        }
    }

    private val contentBox = vbox {
        useMaxHeight = true

        style {
            alignment = Pos.CENTER
            backgroundColor += c(STYLE_BACKGROUND_COLOR)
            spacing = 20.px
        }

        hbox {
            style {
                paddingBottom = 20.0
                alignment = Pos.CENTER
            }
            imageview(Image(PretixDeskMain::class.java.getResourceAsStream("logo.png")))
        }

        this += searchField
        this += resultHolder
    }

    private val syncStatusLabel = jfxButton("") {
        action {
            displaySyncStatus()
        }
    }

    override val root = stackpane {
        vbox {
            useMaxHeight = true

            style {
                alignment = Pos.CENTER
                backgroundColor += c(STYLE_BACKGROUND_COLOR)
                spacing = 20.px
            }

            spacer { }
            this += contentBox
            spacer { }
            hbox {
                addClass(MainStyleSheet.toolBar)

                jfxTogglebutton("SCAN ONLINE") {
                    toggleColor = c(STYLE_STATE_VALID_COLOR)
                    isSelected = !(app as PretixDeskMain).configStore.getAsyncModeEnabled()

                    action {
                        controller.toggleAsync(!isSelected)
                    }
                }
                spacer {}
                this += syncStatusLabel
                spacer {}
                jfxButton("SETTINGS")
            }
        }
    }

    init {
        title = "pretixdesk"

        syncStatusTimeline = timeline {
            cycleCount = Timeline.INDEFINITE

            keyframe(Duration.seconds(.5)) {
                setOnFinished {
                    var text = "?"
                    runAsync {
                        text = controller.syncStatusText()
                    } ui {
                        syncStatusLabel.text = text
                    }
                }
            }
        }

        syncTriggerTimeline = timeline {
            cycleCount = Timeline.INDEFINITE

            keyframe(Duration.seconds(10.0)) {
                setOnFinished {
                    runAsync {
                        controller.triggerSync()
                    }
                }
            }
        }

        currentStage?.setOnCloseRequest {
            syncTriggerTimeline?.stop()
            syncStatusTimeline?.stop()
        }
    }

    private fun removeCard(card: VBox) {
        timeline {
            keyframe(MaterialDuration.EXIT) {
                keyvalue(card.translateXProperty(), 480.0, MaterialInterpolator.EXIT)
                keyvalue(card.opacityProperty(), 0.0, MaterialInterpolator.EXIT)
            }
        }.setOnFinished {
            card.removeFromParent()
            resultCards -= card
        }
    }

    private fun showCard(card: VBox) {
        resultHolder += card
        resultCards += card

        timeline {
            keyframe(MaterialDuration.ENTER) {
                keyvalue(card.translateXProperty(), 0.0, MaterialInterpolator.ENTER)
                keyvalue(card.opacityProperty(), 1.0, MaterialInterpolator.ENTER)
            }
        }.setOnFinished {
            mainSpinner.opacity = 0.0
        }

        timeline {
            keyframe(Duration.seconds(15.0)) {
                setOnFinished{
                    removeCard(card)
                }
            }
        }
    }

    private fun showSpinner() {
        spinnerAnimation?.stop()
        spinnerAnimation = timeline {
            keyframe(MaterialDuration.ENTER) {
                keyvalue(mainSpinner.opacityProperty(), 1.0, MaterialInterpolator.ENTER)
            }
        }
    }

    private fun hideSpinner() {
        spinnerAnimation?.stop()
        spinnerAnimation = timeline {
            keyframe(MaterialDuration.EXIT) {
                keyvalue(mainSpinner.opacityProperty(), 0.0, MaterialInterpolator.EXIT)
            }
        }
    }

    private fun handleInput() {
        val value = searchField.text
        if (value == "") {
            return
        }

        for (oldResultCard in resultCards) {
            removeCard(oldResultCard)
        }

        showSpinner()
        searchField.text = ""

        var resultData: TicketCheckProvider.CheckResult? = null
        runAsync {
            resultData = controller.handleScanInput(value)
        } ui {
            hideSpinner()

            val newCard = makeNewCard(resultData)
            showCard(newCard)

            runAsync {
                controller.triggerSync()
            }
        }
    }

    private fun makeNewCard(data: TicketCheckProvider.CheckResult?): VBox {
        val vb = VBox()
        with(vb) {
            translateX = -480.0
            opacity = 0.2

            vbox {
                addClass(MainStyleSheet.card)
                addClass(MainStyleSheet.resultCard)

                vbox {
                    addClass(MainStyleSheet.cardBody)
                    addClass(when (data?.type) {
                        TicketCheckProvider.CheckResult.Type.INVALID -> MainStyleSheet.cardHeaderErrorNoMessage
                        TicketCheckProvider.CheckResult.Type.VALID -> MainStyleSheet.cardHeaderValid
                        TicketCheckProvider.CheckResult.Type.USED -> MainStyleSheet.cardHeaderRepeat
                        TicketCheckProvider.CheckResult.Type.ERROR -> MainStyleSheet.cardHeaderError
                        TicketCheckProvider.CheckResult.Type.UNPAID -> MainStyleSheet.cardHeaderError
                        TicketCheckProvider.CheckResult.Type.PRODUCT -> MainStyleSheet.cardHeaderError
                        null -> MainStyleSheet.cardHeaderError
                    })

                    val headline = when (data?.type) {
                        TicketCheckProvider.CheckResult.Type.INVALID -> "UNKNOWN TICKET"
                        TicketCheckProvider.CheckResult.Type.VALID -> "VALID"
                        TicketCheckProvider.CheckResult.Type.USED -> "ALREADY SCANNED"
                        TicketCheckProvider.CheckResult.Type.ERROR -> "ERROR"
                        TicketCheckProvider.CheckResult.Type.UNPAID -> "NOT PAID"
                        TicketCheckProvider.CheckResult.Type.PRODUCT -> "INVALID PRODUCT"
                        null -> "UNKNOWN ERROR"
                    }

                    label(headline) {
                        addClass(MainStyleSheet.cardHeaderLabel)
                    }
                }
                if (data?.type != TicketCheckProvider.CheckResult.Type.INVALID) {
                    vbox {
                        addClass(MainStyleSheet.cardBody)

                        if (data?.type == TicketCheckProvider.CheckResult.Type.ERROR) {
                            label(data.message ?: "?")
                        } else {
                            var ticket = data?.ticket ?: ""
                            if (data?.variation != null && data.variation != "null") {
                                ticket += " – " + data.variation
                            }
                            hbox {
                                label(data?.attendee_name ?: "")
                                spacer {}
                                label(data?.orderCode ?: "")
                            }
                            hbox {
                                label(ticket)
                            }
                        }
                    }
                }
                if (data?.isRequireAttention() ?: false) {
                    val attbox = vbox {
                        addClass(MainStyleSheet.cardFooterAttention)
                        addClass(MainStyleSheet.cardBody)
                        label("Attention, special ticket!")
                    }
                    timeline {
                        cycleCount = 10
                        keyframe(Duration.seconds(0.2)) {
                            setOnFinished {
                                attbox.removeClass(MainStyleSheet.cardFooterAttention)
                                attbox.addClass(MainStyleSheet.cardFooterAttentionBlink)
                            }
                        }
                        keyframe(Duration.seconds(0.4)) {
                            setOnFinished {
                                attbox.removeClass(MainStyleSheet.cardFooterAttentionBlink)
                                attbox.addClass(MainStyleSheet.cardFooterAttention)
                            }
                        }
                    }
                }
            }
        }
        return vb
    }

    private fun displaySyncStatus() {
        val closeButton: JFXButton = jfxButton("CLOSE")
        val dialog = jfxDialog (transitionType = JFXDialog.DialogTransition.BOTTOM) {
            setHeading(label("Synchronization status"))
            setBody(label(controller.syncStatusLongText()))
            setActions(closeButton)
        }
        closeButton.action {
            dialog.close()
        }
        dialog.show(root)
    }
}
