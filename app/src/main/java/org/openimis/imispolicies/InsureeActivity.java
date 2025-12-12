// InsureeActivity.java
package org.openimis.imispolicies;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openimis.imispolicies.tools.Log;
import org.openimis.imispolicies.tools.SpinnerItem;
import org.openimis.imispolicies.util.AndroidUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class InsureeActivity extends AppCompatActivity {
    private int insureeId;
    private int familyId;
    private int isHead;
    private int exceedThreshold;
    private int policyId;
    private JSONObject insureeJson;
    private ClientAndroidInterface ca;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insuree);
        
        // Récupérer les données de l'intent
        if (getIntent() != null && getIntent().getExtras() != null) {
            Bundle extras = getIntent().getExtras();
            insureeId = extras.getInt("InsureeId", 0);
            familyId = extras.getInt("FamilyId", 0);
            isHead = extras.getInt("isHead", 0);
            exceedThreshold = extras.getInt("ExceedThreshold", 0);
            policyId = extras.getInt("PolicyId", 0);
        }
        String districtId = "";
        String HFLevel = "";
        rootView = findViewById(R.id.rootInsuree);
        ca = new ClientAndroidInterface(this);
        Spinner districtSpinner = findViewById(R.id.CurDistrict);
        if (districtSpinner.getSelectedItem() instanceof SpinnerItem) {
            SpinnerItem selectedItem = (SpinnerItem) districtSpinner.getSelectedItem();
            districtId = selectedItem.getValue();
        }
        Spinner HFLevelSpinner = findViewById(R.id.FSPCategory);
        if (HFLevelSpinner.getSelectedItem() instanceof SpinnerItem) {
            SpinnerItem selectedItem = (SpinnerItem) HFLevelSpinner.getSelectedItem();
            HFLevel = selectedItem.getValue();
        }
        setupRegionSpinner();
        setupDistrictSpinner();
        setupWardSpinner();
        setupListeners();
        loadInsureeData();
        //fillProfessions();
        fillEducations();
        fillIdentificationTypes();
        //fillRelationship();
        fillVulnerability();
        fillFSP(districtId, HFLevel);
        fillFSPCategory();
        fillGender();
        fillMaritalStatus();
        fillBeneficiaryCard();
        getImage();
        getRegions();

    }

    private void setupListeners() {
        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            String insureeData = collectInsureeData();
            Log.d("InsureeActivity", "Données de l'assuré: " + insureeData);
            try {
                ca.SaveInsuree(insureeData, familyId, isHead, exceedThreshold, policyId);
                AndroidUtils.showToast(this, "Assuré enregistré avec succès");
                finish();  // Ferme l'activité après la sauvegarde
            } catch (Exception e) {
                e.printStackTrace();
                AndroidUtils.showToast(this, "Erreur lors de la sauvegarde");
            }
        });

        EditText dobField = findViewById(R.id.DOB);
        dobField.setOnClickListener(v -> {

            final Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        // JJ-MM-YYYY
                        String date = selectedYear + "-" + 
                                    String.format("%02d", selectedMonth + 1) + "-" + 
                                    String.format("%02d", selectedDay);

                        dobField.setText(date);
                    },
                    year, month, day
            );

            datePicker.show();
        });
    }

    private void loadInsureeData() {
        if (insureeId <= 0) {
            // Si nouvel enregistrement, chargez juste les régions
            getRegions();
            return;
        }

        try {
            String insureeData = ca.getInsuree(insureeId);
            if (insureeData == null || insureeData.isEmpty()) {
                getRegions();
                return;
            }

            JSONArray insureeArray = new JSONArray(insureeData);
            if (insureeArray.length() == 0) {
                getRegions();
                return;
            }

            insureeJson = insureeArray.getJSONObject(0);
            Log.d("InsureeActivity", "loadInsureeData: " + insureeData);

            // Remplir les champs du formulaire
            if (rootView != null) {
                AndroidUtils.bindDataFromDatafield(rootView, insureeJson);
            }

            // Charger les régions et les niveaux inférieurs
            loadLocationHierarchy();

        } catch (Exception e) {
            Log.e("InsureeActivity", "Error loading insuree data", e);
            getRegions(); // En cas d'erreur, chargez au moins les régions
        }
    }

    private void loadLocationHierarchy() {
        try {
            // Chargez les régions
            String regionId = insureeJson.optString("CurRegion", "");
            if (!regionId.isEmpty()) {
                getRegions();
                
                // Attendez un court instant pour que le Spinner soit peuplé
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    // Sélectionnez la région et déclenchez le chargement des districts
                    Spinner regionSpinner = findViewById(R.id.CurRegion);
                    selectSpinnerValue(regionSpinner, regionId, () -> {
                        // Une fois la région sélectionnée, chargez les districts
                        String districtId = insureeJson.optString("CurDistrict", "");
                        if (!districtId.isEmpty()) {
                            Spinner districtSpinner = findViewById(R.id.CurDistrict);
                            selectSpinnerValue(districtSpinner, districtId, () -> {
                                // Une fois le district sélectionné, chargez les wards
                                String wardId = insureeJson.optString("CurWard", "");
                                if (!wardId.isEmpty()) {
                                    Spinner wardSpinner = findViewById(R.id.CurWard);
                                    selectSpinnerValue(wardSpinner, wardId, () -> {
                                        // Une fois le ward sélectionné, chargez les villages
                                        String villageId = insureeJson.optString("CurVillage", "");
                                        if (!villageId.isEmpty()) {
                                            Spinner villageSpinner = findViewById(R.id.CurVillage);
                                            selectSpinnerValue(villageSpinner, villageId, null);
                                        }
                                    });
                                }
                            });
                        }
                    });
                }, 100); // Petit délai pour s'assurer que le Spinner est prêt
            } else {
                getRegions();
            }
        } catch (Exception e) {
            Log.e("InsureeActivity", "Error loading location hierarchy", e);
        }
    }

    private void selectSpinnerValue(Spinner spinner, String value, Runnable onComplete) {
        if (spinner == null || value == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        for (int i = 0; i < spinner.getCount(); i++) {
            SpinnerItem item = (SpinnerItem) spinner.getItemAtPosition(i);
            if (item != null && item.getValue().equals(value)) {
                final int position = i;
                // Utilisez post pour vous assurer que le code s'exécute sur le thread UI
                spinner.post(() -> {
                    spinner.setSelection(position);
                    if (onComplete != null) onComplete.run();
                });
                return;
            }
        }
        
        // Si la valeur n'est pas trouvée, exécutez quand même le callback
        if (onComplete != null) onComplete.run();
    }

    public void getRegions() {
        String jsonRegions = ca.getRegionsWO();
        String selectText = ca.getString("SelectRegion");
        
        try {
            JSONArray array = new JSONArray(jsonRegions);
            if (array.length() == 1) selectText = null;
        } catch (JSONException e) { 
            throw new RuntimeException(e);
        }

        AndroidUtils.bindSpinner(this, findViewById(R.id.CurRegion), 
            jsonRegions, "LocationId", "LocationName", "0", selectText, insureeJson != null ? insureeJson.optString("CurRegion", "") : null);
    }

    public void getDistricts(String regionId) {
        String jsonDistricts = ca.getDistrictsWO(Integer.parseInt(regionId));
        String selectText = ca.getString("SelectDistrict");
        
        try {
            JSONArray array = new JSONArray(jsonDistricts);
            if (array.length() == 1) selectText = null;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        AndroidUtils.bindSpinner(this, findViewById(R.id.CurDistrict), 
            jsonDistricts, "LocationId", "LocationName", "0", selectText, insureeJson != null ? insureeJson.optString("CurDistrict", "") : null);
    }

    public void getWards(String districtId) {
        String jsonWards = ca.getWards(Integer.parseInt(districtId));
        String selectText = ca.getString("SelectWard");
        
        try {
            JSONArray array = new JSONArray(jsonWards);
            if (array.length() == 1) selectText = null;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        AndroidUtils.bindSpinner(this, findViewById(R.id.CurWard), 
            jsonWards, "LocationId", "LocationName", "0", selectText, insureeJson != null ? insureeJson.optString("CurWard", "") : null);
    }

    public void getVillages(String wardId) {
        String jsonVillages = ca.getVillages(Integer.parseInt(wardId));
        String selectText = ca.getString("SelectVillage");
        
        try {
            JSONArray array = new JSONArray(jsonVillages);
            if (array.length() == 1) selectText = null;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        AndroidUtils.bindSpinner(this, findViewById(R.id.CurVillage), 
            jsonVillages, "LocationId", "LocationName", "0", selectText, insureeJson != null ? insureeJson.optString("CurVillage", "") : null);
    }

    public void getDistrictsWO() {
        String jsonDistricts = ca.getDistrictsWO(insureeJson.optInt("CurRegion", 0));
        Log.d("districts", jsonDistricts);
        String selectText = ca.getString("SelectDistrict");

        try {
            JSONArray array = new JSONArray(jsonDistricts);
            if (array.length() == 1) selectText = null;
        } catch (JSONException e) { throw new RuntimeException(e); }

        AndroidUtils.bindSpinner(this, findViewById(R.id.CurDistrict), jsonDistricts, "LocationId", "LocationName", "0", selectText, insureeJson != null ? insureeJson.optString("CurDistrict", "") : null);
    }

    // Remplir les professions
    // private void fillProfessions() {
    //     String textLanguage = "Profession";
    //     if (!ca.getSelectedLanguage().equals("en")) {
    //         textLanguage = "AltLanguage";
    //     }
    //     String jsonProfessions = ca.getProfessions();
    //     String selectText = ca.getString("SelectProfession");
    //     AndroidUtils.bindSpinner(this, findViewById(R.id.Profession),
    //         jsonProfessions, "ProfessionId", textLanguage, "0", selectText, 
    //         insureeJson != null ? insureeJson.optString("Profession", "") : null);
    // }

    // Remplir les niveaux d'éducation
    private void fillEducations() {
        String textLanguage = "Education";
        String jsonEducations = ca.getEducations();
        String selectText = ca.getString("SelectEducation");
        AndroidUtils.bindSpinner(this, findViewById(R.id.Education),
            jsonEducations, "EducationId", textLanguage, "0", selectText,
            insureeJson != null ? insureeJson.optString("Education", "") : null);
    }

    // Remplir les types d'identification
    private void fillIdentificationTypes() {
        String textLanguage = "IdentificationTypes";
        String jsonIdTypes = ca.getIdentificationTypes();
        String selectText = ca.getString("SelectIdentificationType");
        AndroidUtils.bindSpinner(this, findViewById(R.id.TypeOfId),
            jsonIdTypes, "IdentificationCode", textLanguage, "", selectText,
            insureeJson != null ? insureeJson.optString("IdentificationType", "") : null);
    }

    // Remplir les districts FSP
    private void fillFSPDistricts(String regionId) {
        String jsonDistricts = ca.getDistrictsWO(Integer.parseInt(regionId));
        String selectText = ca.getString("SelectDistrict");
        AndroidUtils.bindSpinner(this, findViewById(R.id.FSPDistrict),
            jsonDistricts, "LocationId", "LocationName", "0", selectText, null);
    }

    // Remplir les catégories FSP
    private void fillFSPCategory() {
        String jsonHFLevels = ca.getHFLevels();
        AndroidUtils.bindSpinner(this, findViewById(R.id.FSPCategory),
            jsonHFLevels, "Code", "HFLevel", null, null, insureeJson != null ? insureeJson.optString("HFLevel", "") : null);
    }

    // Remplir les FSP
    private void fillFSP(String districtId, String hfLevel) {
        if (Objects.equals(districtId, null) || districtId.isEmpty() || Objects.equals(hfLevel, null) || hfLevel.isEmpty()) {
            return;
        }
        String jsonHF = ca.getHF(Integer.parseInt(districtId), hfLevel);
        String selectText = ca.getString("SelectHF");
        AndroidUtils.bindSpinner(this, findViewById(R.id.FSP),
            jsonHF, "HFID", "HF", "0", selectText, insureeJson != null ? insureeJson.optString("HF", "") : null);
    }

    // Remplir la vulnérabilité
    private void fillVulnerability() {
        String jsonVulnerability = ca.getVulnerability();
        Log.d("InsureeActivity", "Données de la vulnérabilité: " + jsonVulnerability);
        String selectText = ca.getString("SelectVulnerability");
        AndroidUtils.bindSpinner(this, findViewById(R.id.Vulnerability),
            jsonVulnerability, "value", "key", "", selectText,
            insureeJson != null ? insureeJson.optString("Vulnerability", "") : null);
    }

    // Remplir les relations
    // private void fillRelationship() {
    //     String textLanguage = "Relation";
    //     String jsonRelations = ca.getRelationships();
    //     String selectText = ca.getString("SelectRelationship");
    //     AndroidUtils.bindSpinner(this, findViewById(R.id.Relationship),
    //         jsonRelations, "RelationId", textLanguage, "0", selectText,
    //         insureeJson != null ? insureeJson.optString("Relationship", "") : null);
    // }

    // Remplir le genre
    private void fillGender() {
        String textLanguage = "Gender";
//        if (!ca.getSelectedLanguage().equals("en")) {
//            textLanguage = "AltLanguage";
//        }
        String jsonGender = ca.getGender();
        String selectText = ca.getString("SelectGender");
        AndroidUtils.bindSpinner(this, findViewById(R.id.Gender),
            jsonGender, "Code", textLanguage, null, selectText,
            insureeJson != null ? insureeJson.optString("Gender", "") : null);
    }

    // Remplir l'état civil
    private void fillMaritalStatus() {
        String jsonMaritalStatus = ca.getMaritalStatus();
        AndroidUtils.bindSpinner(this, findViewById(R.id.MaritalStatus),
            jsonMaritalStatus, "Code", "Status", null, null,
            insureeJson != null ? insureeJson.optString("MaritalStatus", "") : null);
    }

    // Remplir la carte de bénéficiaire
    private void fillBeneficiaryCard() {
        String jsonYesNo = ca.getYesNo();
        String selectText = ca.getString("SelectBeneficiary");
        AndroidUtils.bindSpinner(this, findViewById(R.id.CardIssued),
            jsonYesNo, "value", "key", null, selectText,
            insureeJson != null ? insureeJson.optString("BeneficiaryCard", "") : null);
    }

    public void getImage() {
        EditText txtInsuranceNumber = findViewById(R.id.CHFID);
        String ins = txtInsuranceNumber.getText().toString();

        String imagePath = ca.GetListOfImagesContain(ins); // méthode de récupération des images

        FrameLayout cardPhoto = findViewById(R.id.cardPhoto);

        if (imagePath != null && !imagePath.isEmpty()) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap != null) {
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);

                // Pour garder les coins arrondis du background original
                Drawable background = getResources().getDrawable(R.drawable.card_bg);
                LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{background, drawable});
                cardPhoto.setBackground(layerDrawable);
            } else {
                cardPhoto.setBackgroundResource(R.drawable.card_bg);
            }
        } else {
            cardPhoto.setBackgroundResource(R.drawable.card_bg);
        }
    }

    private void setupRegionSpinner() {
        Spinner regionSpinner = findViewById(R.id.CurRegion);
        regionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SpinnerItem selectedItem = (SpinnerItem) parent.getItemAtPosition(position);
                if (selectedItem != null && !selectedItem.getValue().equals("0")) {
                    // resetSpinner(findViewById(R.id.CurDistrict), ca.getString("SelectDistrict"));
                    // resetSpinner(findViewById(R.id.CurWard), ca.getString("SelectWard"));
                    // resetSpinner(findViewById(R.id.CurVillage), ca.getString("SelectVillage"));
                    getDistricts(selectedItem.getValue());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupDistrictSpinner() {
        Spinner districtSpinner = findViewById(R.id.CurDistrict);
        districtSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SpinnerItem selectedItem = (SpinnerItem) parent.getItemAtPosition(position);
                if (selectedItem != null && !selectedItem.getValue().equals("0")) {
                    // resetSpinner(findViewById(R.id.CurWard), ca.getString("SelectWard"));
                    // resetSpinner(findViewById(R.id.CurVillage), ca.getString("SelectVillage"));
                    getWards(selectedItem.getValue());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupWardSpinner() {
        Spinner wardSpinner = findViewById(R.id.CurWard);
        wardSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SpinnerItem selectedItem = (SpinnerItem) parent.getItemAtPosition(position);
                if (selectedItem != null && !selectedItem.getValue().equals("0")) {
                    // resetSpinner(findViewById(R.id.CurVillage), ca.getString("SelectVillage"));
                    getVillages(selectedItem.getValue());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void resetSpinner(Spinner spinner, String defaultText) {
        if (spinner != null) {
            // Créer un nouvel adaptateur vide avec le message par défaut
            List<SpinnerItem> emptyList = new ArrayList<>();
            emptyList.add(new SpinnerItem("", defaultText));
            ArrayAdapter<SpinnerItem> adapter = new ArrayAdapter<>(
                this, 
                android.R.layout.simple_spinner_item, 
                emptyList
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }
    }

    private String collectInsureeData() {
        try {
            JSONObject json = new JSONObject();
            
            // Récupérer les champs de base
            json.put("hfInsureeId", insureeId);
            json.put("hfisHead", isHead);
            json.put("FamilyId", familyId);
            json.put("txtInsuranceNumber", getTextFromView(R.id.CHFID));
            json.put("txtLastName", getTextFromView(R.id.LastName));
            json.put("txtOtherNames", getTextFromView(R.id.OtherNames));
            json.put("txtBirthDate", getTextFromView(R.id.DOB));
            json.put("txtPhoneNumber", getTextFromView(R.id.Phone));
            json.put("txtEmail", getTextFromView(R.id.Email));
            json.put("txtCurrentAddress", getTextFromView(R.id.CurrentAddress));
            json.put("txtIdentificationNumber", getTextFromView(R.id.IdentificationNumber));
            
            // Gestion des Spinners
            json.put("ddlGender", getSpinnerValue(R.id.Gender));
            json.put("ddlMarital", getSpinnerValue(R.id.MaritalStatus));
            json.put("ddlEducation", getSpinnerValue(R.id.Education));
            json.put("ddlCardIssued", getSpinnerValue(R.id.CardIssued));
            json.put("ddlIdentificationType", getSpinnerValue(R.id.TypeOfId));
            json.put("ddlVulnerability", getSpinnerValue(R.id.Vulnerability));
            json.put("ddlCurRegion", getSpinnerValue(R.id.CurRegion));
            json.put("ddlCurDistrict", getSpinnerValue(R.id.CurDistrict));
            json.put("ddlCurWard", getSpinnerValue(R.id.CurWard));
            json.put("ddlCurVillage", getSpinnerValue(R.id.CurVillage));
            json.put("ddlFSPCategory", getSpinnerValue(R.id.FSPCategory));
            json.put("ddlHFID", getTextFromView(R.id.HFID));
//            json.put("ddlRelationship", getSpinnerValue(R.id.Relationship));
//            json.put("ddlProfession", getSpinnerValue(R.id.Profession));
            json.put("ddlEducation", getSpinnerValue(R.id.Education));
            
            // Valeurs fixes
            json.put("isHead", isHead);
            json.put("FamilyId", familyId);
            json.put("isOffline", "1");
            json.put("PhotoPath", "0");
            
            return json.toString();
        } catch (JSONException e) {
            Log.e("InsureeActivity", "Error creating JSON", e);
            return "{}";
        }
    }

    private String getTextFromView(int viewId) {
        View view = findViewById(viewId);
        if (view instanceof EditText) {
            return ((EditText) view).getText().toString().trim();
        } else if (view instanceof TextView) {
            return ((TextView) view).getText().toString().trim();
        }
        return "";
    }

    private String getSpinnerValue(int spinnerId) {
        Spinner spinner = findViewById(spinnerId);
        if (spinner != null && spinner.getSelectedItem() instanceof SpinnerItem) {
            SpinnerItem item = (SpinnerItem) spinner.getSelectedItem();
            // Si c'est l'élément par défaut (texte de sélection), on retourne null
            if (item.getValue().isEmpty() || item.getValue().equals("0")) {
                return "0"; // ou null selon vos besoins
            }
            return item.getValue();
        }
        return "0"; // Valeur par défaut
    }

}