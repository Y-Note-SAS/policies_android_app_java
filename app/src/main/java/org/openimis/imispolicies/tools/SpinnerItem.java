package org.openimis.imispolicies.tools;

public class SpinnerItem {
    private String value;
    private String text;

    public SpinnerItem(String value, String text) {
        this.value = value;
        this.text = text;
    }

    @Override
    public String toString() {
        return text; // display in the spinner
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}