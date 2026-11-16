package com.example.tomodoom.ui.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class NotificationsViewModel extends ViewModel {

    private final MutableLiveData<String> text;

    public NotificationsViewModel() {
        text = new MutableLiveData<>();
        text.setValue("This is notifications Fragment");
    }

    public LiveData<String> getText() {
        return text;
    }
}
