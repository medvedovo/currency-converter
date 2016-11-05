package ru.medvedovo.eugene.currencyconverter.models;

public class Currency {
    public String ID;
    public int NumCode;
    public String CharCode;
    public int Nominal;
    public String Name;
    public float Value;

    @Override
    public String toString() {
        return CharCode;
    }
}
