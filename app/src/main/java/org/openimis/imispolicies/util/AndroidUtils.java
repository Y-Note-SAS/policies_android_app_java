package org.openimis.imispolicies.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openimis.imispolicies.R;
import org.openimis.imispolicies.SQLHandler;
import org.openimis.imispolicies.tools.Log;
import org.openimis.imispolicies.tools.SpinnerItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class AndroidUtils {
    public static ProgressDialog showProgressDialog(
            @NonNull Context context,
            @StringRes int titleResId,
            @StringRes int messageResId
    ) {
        return ProgressDialog.show(
                context,
                titleResId != 0 ? context.getResources().getString(titleResId) : null,
                context.getResources().getString(messageResId)
        );
    }

    public static void showToast(@NonNull Context context, @StringRes int messageResId) {
        Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show();
    }

    public static void showToast(@NonNull Context context, @NonNull CharSequence message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void showDialog(
            @NonNull Context context,
            @Nullable CharSequence title,
            @Nullable CharSequence message,
            boolean isCancelable,
            @Nullable CharSequence positiveLabel,
            @Nullable DialogInterface.OnClickListener onPositive,
            @Nullable CharSequence neutralLabel,
            @Nullable DialogInterface.OnClickListener onNeutral,
            @Nullable CharSequence negativeLabel,
            @Nullable DialogInterface.OnClickListener onNegative
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        builder.setCancelable(isCancelable);
        if (positiveLabel != null) builder.setPositiveButton(positiveLabel, onPositive);
        if (neutralLabel != null) builder.setPositiveButton(neutralLabel, onNeutral);
        if (negativeLabel != null) builder.setNegativeButton(negativeLabel, onNegative);
        if (context instanceof Activity && Thread.currentThread() != Looper.getMainLooper().getThread()) {
            ((Activity) context).runOnUiThread(builder::show);
        } else {
            builder.show();
        }
    }

    public static void showDialog(@NonNull Context context, @StringRes int messageResId) {
        showDialog(context, null, context.getResources().getString(messageResId), false, context.getResources().getString(R.string.Ok), null, null, null, null, null);
    }

    public static void showDialog(@NonNull Context context, @NonNull CharSequence message) {
        showDialog(context, null, message, false, context.getResources().getString(R.string.Ok), null, null, null, null, null);
    }

    public static void showDialog(@NonNull Context context, @NonNull CharSequence title, @NonNull CharSequence message) {
        showDialog(context, title, message, false, context.getResources().getString(R.string.Ok), null, null, null, null, null);
    }

    public static void showConfirmDialog(@NonNull Context context, @StringRes int messageResId, @NonNull DialogInterface.OnClickListener onPositive) {
        showDialog(context, null, context.getResources().getString(messageResId), false, context.getResources().getString(R.string.Ok), onPositive, null, null, context.getResources().getString(R.string.Cancel), null);
    }

    public static void showDialog(@NonNull Context context, @NonNull String message, @NonNull String positiveLabel, @NonNull DialogInterface.OnClickListener onPositive) {
        showDialog(context, null, message, false, positiveLabel, onPositive, null, null, context.getResources().getString(R.string.Cancel), null);
    }

    public static void bindDataFromDatafield(View view, JSONObject data) throws JSONException {
        Log.d("bindDataFromDatafield", data.toString());

        if (view instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                bindDataFromDatafield(root.getChildAt(i), data);
            }
            return;
        }

        String dataField = null;
        if (view.getTag() instanceof String) {
            dataField = (String) view.getTag();
        } else if (view.getContentDescription() != null) {
            dataField = view.getContentDescription().toString();
        }

        if (dataField == null || !data.has(dataField)) return;

        String value = data.optString(dataField, "");

        if (view instanceof EditText) {
            ((EditText) view).setText(value);
            return;
        }

        if (view instanceof TextView && !(view instanceof Spinner)) {
            ((TextView) view).setText(value);
            return;
        }

        if (view instanceof Spinner) {
            Spinner spinner = (Spinner) view;
            SpinnerAdapter adapter = spinner.getAdapter();
            if (adapter == null) return;

            int selectedIndex = -1;
            for (int pos = 0; pos < adapter.getCount(); pos++) {
                Object item = adapter.getItem(pos);
                if (item instanceof SpinnerItem) {
                    SpinnerItem spItem = (SpinnerItem) item;
                    if (value.equals(spItem.getValue())) {
                        selectedIndex = pos;
                        break;
                    }
                } else if (item instanceof String) {
                    if (value.equals(item)) {
                        selectedIndex = pos;
                        break;
                    }
                }
            }

            if (selectedIndex >= 0) {
                spinner.setSelection(selectedIndex);
            }
        }
    }

    public static String collectDataFromFields(android.view.View rootView) {
        JSONObject jsonObject = new JSONObject();
        try {
            collectValues((ViewGroup) rootView, jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "[" + jsonObject.toString() + "]"; // on renvoie un JSONArray comme getInsuree
    }

    // fonction récursive pour parcourir tous les enfants
    private static void collectValues(ViewGroup root, JSONObject json) throws JSONException {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);

            if (v instanceof ViewGroup) {
                collectValues((ViewGroup) v, json);
            }

            String dataField = null;
            if (v.getTag() instanceof String) {
                dataField = (String) v.getTag();
            } else if (v.getContentDescription() != null) {
                dataField = v.getContentDescription().toString();
            }

            if (dataField != null) {
                if (v instanceof EditText) {
                    json.put(dataField, ((EditText) v).getText().toString());
                } else if (v instanceof TextView) {
                    json.put(dataField, ((TextView) v).getText().toString());
                } else if (v instanceof Spinner) {
                    Spinner spinner = (Spinner) v;
                    if (spinner.getSelectedItem() != null)
                        json.put(dataField, spinner.getSelectedItem().toString());
                }
            }
        }
    }

    public static void loadSpinner(Context context, Spinner spinner, int arrayResId) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                context,
                arrayResId,
                android.R.layout.simple_spinner_item
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    public static void bindSpinner(Context context, Spinner spinner, String jsonSource,
                                String valueKey, String textKey, String selectValue, 
                                String selectText, String selectedValue) {
        List<SpinnerItem> items = new ArrayList<>();
        
        try {
            JSONArray jsonArray = new JSONArray(jsonSource);
            
            if (selectText != null) {
                items.add(new SpinnerItem(selectValue != null ? selectValue : "", selectText));
            }
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String value = obj.getString(valueKey);
                String text = obj.getString(textKey);
                items.add(new SpinnerItem(value, text));
            }
            
            ArrayAdapter<SpinnerItem> adapter = getSpinnerItemArrayAdapter(context, items);
            spinner.setAdapter(adapter);
            
            // Sélectionnez la valeur si spécifiée
            if (selectedValue != null && !selectedValue.isEmpty()) {
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).getValue().equals(selectedValue)) {
                        spinner.setSelection(i);
                        break;
                    }
                }
            }
            
        } catch (JSONException e) {
            Log.e("AndroidUtils", "Error binding spinner", e);
        }
    }

    @NonNull
    private static ArrayAdapter<SpinnerItem> getSpinnerItemArrayAdapter(Context context, List<SpinnerItem> items) {
        ArrayAdapter<SpinnerItem> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                items
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setText(getItem(position).getText());
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setText(getItem(position).getText());
                return view;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    public static HashMap<String, String> jsonToTable(String jsonString) {
        HashMap<String, String> data = new HashMap<>();

        try {
            JSONObject object = new JSONObject(jsonString);

            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = object.optString(key, null);
                data.put(key, value);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return data;
    }


}
