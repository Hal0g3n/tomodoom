package com.example.tomodoom;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.tomodoom.databinding.ActivityMainBinding;

/**
 * Single activity hosting the two top-level destinations (Pets and Chat) behind a
 * {@link com.google.android.material.bottomnavigation.BottomNavigationView}.
 *
 * <p>The action bar is hidden on the Pets destination (its HP header acts as the page header)
 * and shown on every other destination.</p>
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

        // Each bottom-nav item is a top-level destination (no Up arrow).
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_pets, R.id.navigation_chat
        ).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // No top app bar on Pets: the HP card is the page header there.
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar == null) {
                return;
            }
            if (destination.getId() == R.id.navigation_pets) {
                actionBar.hide();
            } else {
                actionBar.show();
            }
        });
    }
}
