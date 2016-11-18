package ru.medvedovo.eugene.currencyconverter;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;

import ru.medvedovo.eugene.currencyconverter.models.Currency;
import ru.medvedovo.eugene.currencyconverter.xml.CurrencyXmlConstants;
import ru.medvedovo.eugene.currencyconverter.xml.XMLDOMParser;

public class MainActivity extends AppCompatActivity {
    Calendar calendar = Calendar.getInstance();
    Button buttonSetDate;
    Spinner currencyFrom;
    Spinner currencyTo;
    String selectedFrom;
    String selectedTo;
    EditText valueFrom;
    EditText valueTo;

    LinkedHashMap<String, Currency> currencies;

    static final String URL = "http://www.cbr.ru/scripts/XML_daily.asp?date_req=%02d/%02d/%04d";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        buttonSetDate = (Button) findViewById(R.id.buttonSetDate);

        setInitialDateTime();
        initializeControls();
    }

    private void initializeControls() {
        // Initializes event listeners and controls
        View.OnClickListener buttonSetDateListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDate();
            }
        };

        buttonSetDate.setOnClickListener(buttonSetDateListener);

        currencyFrom = (Spinner) findViewById(R.id.currencyFrom);
        currencyTo = (Spinner) findViewById(R.id.currencyTo);
        currencyFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                selectedFrom = new ArrayList<>(currencies.values()).get(position).ID;
                updateValueTo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                selectedFrom = "";
            }
        });
        currencyTo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                selectedTo = new ArrayList<>(currencies.values()).get(position).ID;
                updateValueTo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                selectedTo = "";
            }
        });

        valueFrom = (EditText) findViewById(R.id.valueFrom);
        valueTo = (EditText) findViewById(R.id.valueTo);
        valueTo.setFocusable(false);
        valueFrom.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                updateValueTo();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
    }

    private int findCurrencyIndexById(String id) {
        if (currencies == null || currencies.size() == 0) {
            return 0;
        }
        int index = 0;
        for (Object key : currencies.keySet()) {
            if (key.toString().equals(id)) {
                return index;
            }
            ++index;
        }
        return 0;
    }

    private void updateValueTo() {
        if (currencies != null && currencies.size() > 0) {
            Currency from = currencies.get(selectedFrom);
            Currency to = currencies.get(selectedTo);

            if (from == null || to == null) {
                return;
            }

            if (!valueFrom.getText().toString().isEmpty()) {
                float result = Float.parseFloat(valueFrom.getText().toString().replace(',', '.')) * (from.Value / from.Nominal) / (to.Value / to.Nominal);
                valueTo.setText(String.format("%.2f", result));
            } else {
                valueTo.setText("");
            }
        }
    }

    public void LoadCurrencies() {
        GetXMLTask task = new GetXMLTask(this);
        String url = String.format(URL, calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR));
        task.execute(url);
    }

    public void setDate() {
        new DatePickerDialog(MainActivity.this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            setInitialDateTime();
        }
    };

    private void setInitialDateTime() {
        buttonSetDate.setText(DateUtils.formatDateTime(this, calendar.getTimeInMillis(), DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_YEAR));
        LoadCurrencies();
    }

    private class GetXMLTask extends AsyncTask<String, Void, String> {
        private Activity context;

        GetXMLTask(Activity context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... urls) {
            String xml = null;
            for (String url : urls) {
                xml = getXmlFromUrl(url);
            }
            return xml;
        }

        @Override
        protected void onPostExecute(String xml) {
            XMLDOMParser parser = new XMLDOMParser();
            InputStream stream;
            stream = new ByteArrayInputStream(Charset.forName("CP1251").encode(xml).array());
            Document doc = parser.getDocument(stream);

            NodeList nodeList = doc.getElementsByTagName(CurrencyXmlConstants.NODE_CUR);

            Currency currency;
            currencies = new LinkedHashMap<String, Currency>();
            for (int i = 0; i < nodeList.getLength(); i++) {
                currency = new Currency();
                Element e = (Element) nodeList.item(i);
                currency.ID = e.getAttribute(CurrencyXmlConstants.ATTR_ID);
                currency.NumCode = parser.getValue(e, CurrencyXmlConstants.NODE_NUMCODE);
                currency.CharCode = parser.getValue(e, CurrencyXmlConstants.NODE_CHCODE);
                currency.Nominal = Integer.parseInt(parser.getValue(e, CurrencyXmlConstants.NODE_NOM));
                currency.Name = parser.getValue(e, CurrencyXmlConstants.NODE_NAME);
                currency.Value = Float.parseFloat(parser.getValue(e, CurrencyXmlConstants.NODE_VALUE).replaceAll("[^0-9,]", "").replace(',', '.'));
                currencies.put(currency.ID, currency);
            }

            if (nodeList.getLength() == 0) {
                Toast.makeText(getBaseContext(), "Нет данных для указанной даты", Toast.LENGTH_SHORT).show();
            }

            ArrayAdapter<Currency> dataAdapter = new ArrayAdapter<Currency>(context, android.R.layout.simple_spinner_item, new ArrayList<Currency>(currencies.values()));
            currencyFrom.setAdapter(dataAdapter);
            currencyTo.setAdapter(dataAdapter);
            currencyFrom.setSelection(findCurrencyIndexById(selectedFrom));
            currencyTo.setSelection(findCurrencyIndexById(selectedTo));
            updateValueTo();
        }

        private String getXmlFromUrl(String urlString) {
            StringBuilder output = new StringBuilder("");

            InputStream stream;
            URL url;
            try {
                url = new URL(urlString);
                URLConnection connection = url.openConnection();

                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                httpConnection.setRequestMethod("GET");
                httpConnection.connect();

                if (httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    stream = httpConnection.getInputStream();
                    BufferedReader buffer = new BufferedReader(new InputStreamReader(stream));
                    String s;
                    while ((s = buffer.readLine()) != null)
                        output.append(s);
                }
            } catch (MalformedURLException e) {
                Log.e("Error", "Unable to parse URL", e);
                Toast.makeText(getBaseContext(), "Невозможно обработать URL", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("Error", "IO Exception", e);
                Toast.makeText(getBaseContext(), "Ошибка ввода/вывода", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getBaseContext(), "Внутренняя ошибка", Toast.LENGTH_SHORT).show();
            }

            return output.toString();
        }
    }
}
