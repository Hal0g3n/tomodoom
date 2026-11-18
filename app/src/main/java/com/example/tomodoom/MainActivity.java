package com.example.tomodoom;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.tomodoom.databinding.ActivityMainBinding;

/**
 * Single activity hosting the two top-level destinations (Pets and Chat) behind a
 * {@link com.google.android.material.bottomnavigation.BottomNavigationView}.
 *
 * <p>There is no action bar (the theme is {@code NoActionBar}): each page supplies its own
 * header.</p>
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Fragment host = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
        if (!(host instanceof NavHostFragment)) {
            throw new IllegalStateException("activity_main must host a NavHostFragment");
        }
        NavController navController = ((NavHostFragment) host).getNavController();
        NavigationUI.setupWithNavController(binding.navView, navController);
    }
}
