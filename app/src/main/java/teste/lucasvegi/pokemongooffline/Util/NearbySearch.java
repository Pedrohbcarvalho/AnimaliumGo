package teste.lucasvegi.pokemongooffline.Util;

import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.libraries.places.api.net.SearchNearbyResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.gms.maps.model.LatLng;
import java.util.Arrays;
import java.util.List;

public class NearbySearch {
    public static final List<Place.Field> FIELDS = Arrays.asList(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.PHOTO_METADATAS
    );

    public static Task<SearchNearbyResponse> run(LatLng latLng) {
        PlacesClient placesClient = Places.createClient(MyApp.getAppContext());

        // Configuração do raio (300m) e ranking (POPULARITY)
        CircularBounds circle = CircularBounds.newInstance(latLng, 300.0);

        SearchNearbyRequest request = SearchNearbyRequest.builder(circle, FIELDS)
                .setRankPreference(SearchNearbyRequest.RankPreference.POPULARITY)
                .setMaxResultCount(15)
                .build();

        return placesClient.searchNearby(request);
    }
}