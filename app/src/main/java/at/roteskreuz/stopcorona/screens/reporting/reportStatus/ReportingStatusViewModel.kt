package at.roteskreuz.stopcorona.screens.reporting.reportStatus

import at.roteskreuz.stopcorona.model.entities.infection.message.MessageType
import at.roteskreuz.stopcorona.model.repositories.AgreementData
import at.roteskreuz.stopcorona.model.repositories.CoronaDetectionRepository
import at.roteskreuz.stopcorona.model.repositories.QuarantineRepository
import at.roteskreuz.stopcorona.model.repositories.ReportingRepository
import at.roteskreuz.stopcorona.model.repositories.other.ContextInteractor
import at.roteskreuz.stopcorona.model.services.stopCoronaDetectionService
import at.roteskreuz.stopcorona.skeleton.core.model.helpers.AppDispatchers
import at.roteskreuz.stopcorona.skeleton.core.model.helpers.DataState
import at.roteskreuz.stopcorona.skeleton.core.model.helpers.DataStateObserver
import at.roteskreuz.stopcorona.skeleton.core.screens.base.viewmodel.ScopedViewModel
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import kotlinx.coroutines.launch
import org.threeten.bp.ZonedDateTime

/**
 * Handles the user interaction and provides data for [ReportingStatusFragment].
 */
class ReportingStatusViewModel(
    appDispatchers: AppDispatchers,
    private val reportingRepository: ReportingRepository,
    private val quarantineRepository: QuarantineRepository,
    private val coronaDetectionRepository: CoronaDetectionRepository,
    private val contextInteractor: ContextInteractor
) : ScopedViewModel(appDispatchers) {

    private val uploadReportDataStateObserver = DataStateObserver<MessageType>()

    fun setUserAgreement(agreement: Boolean) {
        reportingRepository.setUserAgreement(agreement)
    }

    fun uploadInfectionInformation() {
        uploadReportDataStateObserver.loading()
        launch {
            try {
                val reportedInfectionLevel = reportingRepository.uploadReportInformation()
                uploadReportDataStateObserver.loaded(reportedInfectionLevel)
            } catch (ex: Exception) {
                uploadReportDataStateObserver.error(ex)
            } finally {
                uploadReportDataStateObserver.idle()
            }
        }
    }

    fun goBack() {
        reportingRepository.goBackFromReportingAgreementScreen()
    }

    fun observeUploadReportDataState(): Observable<DataState<MessageType>> {
        return uploadReportDataStateObserver.observe().map { dataState ->

            /**
             * Stop the automatic handshake when the user reported himself proven sick.
             */
            if (dataState is DataState.Loaded &&
                dataState.data is MessageType.InfectionLevel.Red &&
                coronaDetectionRepository.isServiceRunning) {
                contextInteractor.applicationContext.stopCoronaDetectionService()
            }

            dataState
        }
    }

    fun observeReportingStatusData(): Observable<ReportingStatusData> {
        return Observables.combineLatest(
            reportingRepository.observeAgreementData(),
            reportingRepository.observeMessageType(),
            quarantineRepository.observeDateOfFirstSelfDiagnose()
        ).map { (agreementData, infectionLevel, dateOfFirstSelfDiagnose) ->
            ReportingStatusData(
                agreementData,
                infectionLevel,
                dateOfFirstSelfDiagnose.orElse(null)
            )
        }
    }
}

data class ReportingStatusData(val agreementData: AgreementData, val messageType: MessageType, val dateOfFirstSelfDiagnose: ZonedDateTime?)
