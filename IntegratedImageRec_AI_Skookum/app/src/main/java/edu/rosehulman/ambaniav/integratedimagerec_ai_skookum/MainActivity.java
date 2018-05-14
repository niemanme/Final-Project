package edu.rosehulman.ambaniav.integratedimagerec_ai_skookum;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends GolfBallDeliveryActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Note: The superclass does this, if I do it too. That's bad!
        //setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_next) {
//      Toast.makeText(this, "You pressed Next", Toast.LENGTH_SHORT).show();
            mViewFlipper.showNext();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
