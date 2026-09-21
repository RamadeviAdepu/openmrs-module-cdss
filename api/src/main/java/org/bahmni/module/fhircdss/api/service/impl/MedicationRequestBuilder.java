package org.bahmni.module.fhircdss.api.service.impl;

import org.apache.log4j.Logger;
import org.bahmni.module.fhircdss.api.service.RequestBuilder;
import org.bahmni.module.fhircdss.api.util.CdssUtils;
import org.bahmni.module.fhircdss.api.util.Frequency;
import org.bahmni.module.fhircdss.api.util.DosageRouteMapper;
import org.bahmni.module.fhircdss.api.util.DosageUnitMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Timing;
import org.openmrs.CareSetting;
import org.openmrs.Drug;
import org.openmrs.DrugOrder;
import org.openmrs.DrugReferenceMap;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.module.fhir2.api.FhirConceptSourceService;
import org.openmrs.module.fhir2.api.FhirMedicationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.bahmni.module.fhircdss.api.service.CdssOrderSelectService.CODING_SYSTEM_FOR_OPENMRS_CONCEPT;

@Component
public class MedicationRequestBuilder implements RequestBuilder<Bundle> {
    private static Logger logger = Logger.getLogger(MedicationRequestBuilder.class);


    private static final String DRUG_ORDER = "Drug order";

    private PatientService patientService;

    private OrderService orderService;

    private FhirConceptSourceService fhirConceptSourceService;

    private FhirMedicationRequestService fhirMedicationRequestService;

    @Autowired
    public MedicationRequestBuilder(PatientService patientService, OrderService orderService, FhirConceptSourceService fhirConceptSourceService, FhirMedicationRequestService fhirMedicationRequestService) {
        this.patientService = patientService;
        this.orderService = orderService;
        this.fhirConceptSourceService = fhirConceptSourceService;
        this.fhirMedicationRequestService = fhirMedicationRequestService;
    }

    @Override
    public Bundle build(Bundle inputBundle) {
        Bundle medicationBundle = new Bundle();
        addExistingActiveMedications(inputBundle, medicationBundle);
        addDraftMedications(inputBundle, medicationBundle);
        return medicationBundle;
    }

    private Bundle addExistingActiveMedications(Bundle inputBundle, Bundle medicationBundle) {
        String patientUuid = CdssUtils.getPatientUuidFromRequest(inputBundle);
        List<Order> activeOrders = getActiveOrders(patientUuid);
        for (Order order : activeOrders) {
            MedicationRequest medicationRequest = fhirMedicationRequestService.get(order.getUuid());
            CodeableConcept codeableConcept = getCodeableConceptForMedicationRequest(order);
            medicationRequest.setMedication(codeableConcept);
            addEntryToMedicationBundle(medicationBundle, medicationRequest);
        }
        return medicationBundle;
    }

    private CodeableConcept getCodeableConceptForMedicationRequest(Order order) {
        CodeableConcept codeableConcept = new CodeableConcept();

        Drug drug = ((DrugOrder) order).getDrug();
        Set<DrugReferenceMap> drugReferenceMaps = drug.getDrugReferenceMaps();
        if (!drugReferenceMaps.isEmpty()) {
            drugReferenceMaps.stream().forEach(drugReferenceMap -> {
                Coding coding = new Coding();
                coding.setCode(drugReferenceMap.getConceptReferenceTerm().getCode());
                coding.setDisplay(drug.getDisplayName());
                coding.setSystem(fhirConceptSourceService.getUrlForConceptSource(drugReferenceMap.getConceptReferenceTerm().getConceptSource()));
                codeableConcept.addCoding(coding);
            });
            codeableConcept.setText(drug.getDisplayName());
        }

        Coding bahmniCoding = new Coding();
        bahmniCoding.setCode(drug.getUuid());
        bahmniCoding.setDisplay(drug.getDisplayName());
        bahmniCoding.setSystem(CODING_SYSTEM_FOR_OPENMRS_CONCEPT);
        codeableConcept.addCoding(bahmniCoding);

        return codeableConcept;
    }

    private void addDraftMedications(Bundle requestBundle, Bundle medicationBundle) {
        List<Bundle.BundleEntryComponent> medicationEntries = requestBundle.getEntry().stream().filter(entry -> ResourceType.MedicationRequest.equals(entry.getResource().getResourceType())).collect(Collectors.toList());
        medicationEntries.stream().forEach(medicationEntry -> addEntryToMedicationBundle(medicationBundle, (MedicationRequest) medicationEntry.getResource()));
    }

    private void addEntryToMedicationBundle(Bundle medicationBundle, MedicationRequest medicationRequest) {
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        resolveFhirDosage(medicationRequest);
        bundleEntryComponent.setResource(medicationRequest);
        medicationBundle.addEntry(bundleEntryComponent);

    }

    private List<Order> getActiveOrders(String patientUuid) {
        Patient openmrsPatient = patientService.getPatientByUuid(patientUuid);
        CareSetting careSetting = orderService.getCareSettingByName(CareSetting.CareSettingType.OUTPATIENT.toString());
        OrderType drugOrderType = orderService.getOrderTypeByName(DRUG_ORDER);
        return orderService.getActiveOrders(openmrsPatient, drugOrderType, careSetting, null);
    }

    private void resolveFhirDosage(MedicationRequest medicationRequest) {
        Dosage dosage = medicationRequest.getDosageInstruction().get(0);
        resolveDoseUnit(dosage);
        resolveDoseRoute(dosage);
        String frequencyText = getFrequencyTextFromDosage(dosage);
        Frequency.FrequencyValue frequency = Frequency.resolveFrequency(frequencyText);
        if (frequency == null) {
            logger.warn("Skipping CDSS dosage frequency normalization for unsupported frequency: '" + frequencyText + "'");
            return;
        }
        resolveFhirDosageFrequency(dosage, frequency);
    }

    private String getFrequencyTextFromDosage(Dosage dosage) {
        if (dosage.getTiming() == null || dosage.getTiming().getCode() == null) {
            return null;
        }
        return dosage.getTiming().getCode().getText();
    }

    private void resolveFhirDosageFrequency(Dosage dosage, Frequency.FrequencyValue frequency) {
        dosage.getTiming().getRepeat().setFrequency(frequency.getFrequencyCount());
        dosage.getTiming().getRepeat().setPeriod(frequency.getPeriodCount());
        dosage.getTiming().getRepeat().setPeriodUnit(Timing.UnitsOfTime.fromCode(frequency.getPeriodUnit()));
        if (frequency.getWhen() != null) {
            dosage.getTiming().getRepeat().addWhen(frequency.getWhen());
        }
    }

    private void resolveDoseUnit(Dosage dosage) {
        Dosage.DosageDoseAndRateComponent dosageDoseAndRateComponent = dosage.getDoseAndRate().get(0);
        Quantity doseQuantity = dosageDoseAndRateComponent.getDoseQuantity();
        String doseUnit = DosageUnitMapper.getTargetUnit(doseQuantity.getUnit());
        if(doseUnit == null) {
            doseQuantity.setUnit("NA");
            return;
        }
        doseQuantity.setUnit(doseUnit);
    }

    private void resolveDoseRoute(Dosage dosage) {
        CodeableConcept dosageRoute = dosage.getRoute();
        Coding coding = dosageRoute.getCoding().get(0);
        String route = DosageRouteMapper.getTargetRoute(coding.getDisplay());
        if(route == null) {
            coding.setDisplay("NA");
            dosageRoute.setText("NA");
            return;
        }
        coding.setDisplay(route);
        dosageRoute.setText(route);
    }
}
