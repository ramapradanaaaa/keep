package com.example.ramapradana.keep;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import com.example.ramapradana.keep.adapter.EventFileAdapter;
import com.example.ramapradana.keep.data.local.database.DatabaseHelper;
import com.example.ramapradana.keep.data.remote.model.EventFileResponse;
import com.example.ramapradana.keep.data.remote.model.EventsItem;
import com.example.ramapradana.keep.data.remote.model.FileItem;
import com.example.ramapradana.keep.data.remote.service.KeepApiClient;

import java.util.ArrayList;
import java.util.List;

import me.anwarshahriar.calligrapher.Calligrapher;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EventDetailActivity extends AppCompatActivity {
    private RecyclerView rvFile;
    private EventFileAdapter adapter;
    private SwipeRefreshLayout srFile;
    private Call<EventFileResponse> call;
    private DatabaseHelper db;
    private List<FileItem> localFIleItems;
    private EventsItem eventsItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        Calligrapher calligrapher = new Calligrapher(this);
        calligrapher.setFont(this, "Product Sans Regular.ttf", true);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        eventsItem = (EventsItem) intent.getSerializableExtra("event");

        toolbar.setTitle(eventsItem.getEventName());
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> {
            onBackPressed();
            finish();
        });

        rvFile = findViewById(R.id.rv_eventfile_item);
        srFile = findViewById(R.id.sr_eventfile);
        db  = new DatabaseHelper(this);

        adapter = new EventFileAdapter(this, getSupportFragmentManager());
        if(isNetworkConnected()){
            loadOnline();
        }else{
            loadFromLocal();
        }

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        linearLayoutManager.setAutoMeasureEnabled(false);
        rvFile.setNestedScrollingEnabled(false);
        rvFile.setLayoutManager(linearLayoutManager);
        rvFile.setAdapter(adapter);

        srFile.setOnRefreshListener(() -> {
            if(isNetworkConnected()){
                loadOnline();
            }else{
                loadFromLocal();
            }
        });

    }

    @Override
    protected void onResume() {
        loadFromLocal();
        super.onResume();
    }

    public void loadOnline(){
        srFile.setRefreshing(true);
        SharedPreferences sharedPreferences = getSharedPreferences("credential", Context.MODE_PRIVATE);
        String token = sharedPreferences.getString("access_token", "");

        call = KeepApiClient.getKeepApiService().getEventFile(eventsItem.getEventId(), token);
        call.enqueue(new Callback<EventFileResponse>() {
            @Override
            public void onResponse(Call<EventFileResponse> call, Response<EventFileResponse> response) {
                if (response.isSuccessful()){
                    if (response.body().isStatus()){
                        db.deleteAllFileOfEvent(new String[] {String.valueOf(eventsItem.getEventId())});

                        List<FileItem> fileItems = response.body().getFile();
                        int fileCount = fileItems.size();
                        boolean inserted;
                        int unSync = 0;
                        for(int i = 0; i < fileCount; i++){
                            FileItem fileItem = fileItems.get(i);
                            inserted = db.insertEventFile(
                                    fileItem.getEventfileId(),
                                    fileItem.getEventId(),
                                    fileItem.getEventfileTitle(),
                                    fileItem.getEventfileContent(),
                                    fileItem.getEventfileFormat(),
                                    fileItem.getUploadBy(),
                                    fileItem.getCreatedAt());

                            if(!inserted){
                                unSync++;
                            }
                        }

                        if(unSync > 0){
                            Toast.makeText(getApplicationContext(), unSync + " file unsync, refresh to sync it now.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
                loadFromLocal();
                srFile.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<EventFileResponse> call, Throwable t) {
                Toast.makeText(getApplicationContext(), "Could not refresh file and note", Toast.LENGTH_LONG).show();
                loadFromLocal();
                srFile.setRefreshing(false);
            }
        });
    }

    public void loadFromLocal(){
        localFIleItems = new ArrayList<>();
        Cursor result = db.getEventFile(eventsItem.getEventId());

        if(result.getCount() == 0){
            Toast.makeText(this, "There is no file or note found!", Toast.LENGTH_LONG).show();
            adapter.setData(localFIleItems);
            return;
        }

        while(result.moveToNext()){
            FileItem file = new FileItem();
            file.setEventfileId(result.getInt(0));
            file.setEventId(result.getInt(1));
            file.setEventfileTitle(result.getString(2));
            file.setEventfileContent(result.getString(3));
            file.setEventfileFormat(result.getString(4));
            file.setUploadBy(result.getInt(5));
            file.setCreatedAt(result.getString(6));

            localFIleItems.add(file);
        }

        adapter.setData(localFIleItems);
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }

}