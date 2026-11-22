package teste.lucasvegi.pokemongooffline.Model;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPhotoRequest;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.maps.model.PlacesSearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import teste.lucasvegi.pokemongooffline.Util.BancoDadosSingleton;
import teste.lucasvegi.pokemongooffline.Util.MyApp;
import teste.lucasvegi.pokemongooffline.Util.NearbySearch;
import teste.lucasvegi.pokemongooffline.Util.RandomUtil;
import teste.lucasvegi.pokemongooffline.Util.TimeUtil;
import teste.lucasvegi.pokemongooffline.Util.TokenManager;

/**
 * Created by Lucas on 08/12/2016.
 */
public final class ControladoraFachadaSingleton {
    // Tempo de espera entre duas interações com o mesmo PokéStop (5 minutos)
    public static final long COOLDOWN_POKESTOP_MS = 5 * 60 * 1000L;

    private Usuario user;
    private Map<String,List<Pokemon>> pokemons;
    private Aparecimento[] aparecimentos = new Aparecimento[10];
    private List<Tipo> tiposPokemon;
    private static ControladoraFachadaSingleton INSTANCE = new ControladoraFachadaSingleton();
    private boolean sorteouLendario = false;
    private List<Ovo> ovos = new ArrayList<>();
    private PerfilUsuario perfilUsuario; // perfil vindo da nuvem (GET /api/usuarios/me)

    // Todos os animais cadastrados (GET /api/animais, carregado na Splash). Usado pelo sorteio do ovo.
    private volatile List<Animal> todosAnimais = new ArrayList<>();

    // Espécies que o usuário já obteve (GET /api/capturas/me). Decide o rótulo "novo"/"conhecido"
    // da captura. Carregado na thread de sorteio ANTES do 1º spawn (ver sorteiaAparecimentosAnimais).
    private final Set<Integer> idsAnimaisCapturados = Collections.synchronizedSet(new HashSet<Integer>());
    private volatile boolean capturasCarregadas = false;


    // DESACOPLADO (2026-11): doces/evolução não são mais usados; mantido só como referência.
    private List<Doce> doces;

    private void daoDoce(){
        if (user == null) return;
        this.doces = new ArrayList<Doce>();

        Cursor c = BancoDadosSingleton.getInstance().buscar("doce",
                new String[]{"idDoce","nome","quant"},
                "loginUsuario = ?", new String[]{user.getLogin()}, "");

        while(c.moveToNext()){
            int idD = c.getColumnIndex("idDoce");
            int nome = c.getColumnIndex("nome");
            int quant = c.getColumnIndex("quant");

            Doce d = new Doce();
            d.setIdDoce(c.getInt(idD));
            d.setNomePkm(c.getString(nome));
            d.setQuantidade(c.getInt(quant));

            this.doces.add(d);
        }

        c.close();

        //TODO: apagar testes de impressão doce

        //IMPRIME DE TESTE
        for (Doce d : doces){
            Log.d("DOCES",d.getNomePkm() + ": " + d.getQuantidade());
        }
    }

    private ControladoraFachadaSingleton() {
        daoTipo();
        daoPokemons(this);
        daoOvo();
    }

    private void daoOvo(){
        if (user == null) return;
        this.ovos.clear();

        Cursor c = BancoDadosSingleton.getInstance().buscar("ovo",
                new String[]{"idOvo","idPokemon","idTipoOvo","incubado","chocado","exibido","KmAndado"},
                "exibido = 0 AND loginUsuario = ?", new String[]{user.getLogin()}, "");

        while(c.moveToNext()){
            int idO = c.getColumnIndex("idOvo");
            int idP = c.getColumnIndex("idPokemon");
            int idTO = c.getColumnIndex("idTipoOvo");
            int idInc = c.getColumnIndex("incubado");
            int idCho = c.getColumnIndex ("chocado");
            int idExi = c.getColumnIndex ("exibido");
            int idKmAnd = c.getColumnIndex ("KmAndado");

            ovos.add(new Ovo(c.getInt(idO), c.getInt(idP), c.getString(idTO),c.getInt(idInc),c.getInt(idCho),c.getInt(idExi),c.getDouble(idKmAnd)));
        }

        c.close();
    }

    private void daoTipo(){
        this.tiposPokemon = new ArrayList<>();

        Cursor c = BancoDadosSingleton.getInstance().buscar("tipo",new String[]{"idTipo","nome"},"","");

        while(c.moveToNext()){
            int idT = c.getColumnIndex("idTipo");
            int name = c.getColumnIndex("nome");

            Tipo t = new Tipo();
            t.setIdTipo(c.getInt(idT));
            t.setNome(c.getString(name));

            this.tiposPokemon.add(t);
        }

        c.close();

        //TODO: apagar testes de impressão tipo

        //IMPRIME DE TESTE
        for (Tipo tp : tiposPokemon){
            Log.d("TIPOS",tp.getNome());
        }
    }

    private void daoPokemons(ControladoraFachadaSingleton controladorGeral){
        pokemons = new HashMap<String,List<Pokemon>>();

        Cursor c = BancoDadosSingleton.getInstance().buscar("pokemon",new String[]{"idPokemon","nome","categoria","foto","icone","idDoce","idPokemonBase"},"","");

        while(c.moveToNext()){
            int idP = c.getColumnIndex("idPokemon");
            int name = c.getColumnIndex("nome");
            int cat = c.getColumnIndex("categoria");
            int foto = c.getColumnIndex("foto");
            int icone = c.getColumnIndex("icone");
            int idDoce = c.getColumnIndex("idDoce");
            int idPokemonBase = c.getColumnIndex("idPokemonBase");

            Pokemon p = new Pokemon(c.getInt(idP),c.getString(name),c.getString(cat),c.getInt(foto),c.getInt(icone),c.getInt(idDoce), c.getInt(idPokemonBase),controladorGeral);

            //verifica se lista de alguma categoria ainda não existe
            if(pokemons.get(p.getCategoria()) == null)
                pokemons.put(p.getCategoria(),new ArrayList<Pokemon>());

            //adiciona o pokemon na lista da sua categoria
            pokemons.get(p.getCategoria()).add(p);
        }

        c.close();

        //TODO: apagar testes de impressão pokemon

        //IMPRIME POKEMONS COMUNS
        for(Pokemon pokemon : pokemons.get("C")){
            String tipos = "";
            for (Tipo tp :  pokemon.getTipos()){
                tipos += tp.getNome();
                tipos += "/";
            }
            Log.d("POKEMONS", pokemon.getNumero() + " - " + pokemon.getNome() + " - " + pokemon.getCategoria() + " - " + pokemon.getFoto() + " - " + pokemon.getIcone() + " - " + tipos);
        }

        //IMPRIME POKEMONS INCOMUNS
        for(Pokemon pokemon : pokemons.get("I")){
            String tipos = "";
            for (Tipo tp :  pokemon.getTipos()){
                tipos += tp.getNome();
                tipos += "/";
            }
            Log.d("POKEMONS", pokemon.getNumero() + " - " + pokemon.getNome() + " - " + pokemon.getCategoria() + " - " + pokemon.getFoto() + " - " + pokemon.getIcone() + " - " + tipos);
        }

        //IMPRIME POKEMONS RAROS
        for(Pokemon pokemon : pokemons.get("R")){
            String tipos = "";
            for (Tipo tp :  pokemon.getTipos()){
                tipos += tp.getNome();
                tipos += "/";
            }
            Log.d("POKEMONS", pokemon.getNumero() + " - " + pokemon.getNome() + " - " + pokemon.getCategoria() + " - " + pokemon.getFoto() + " - " + pokemon.getIcone() + " - " + tipos);
        }

        //IMPRIME POKEMONS LENDARIOS
        for(Pokemon pokemon : pokemons.get("L")){
            String tipos = "";
            for (Tipo tp :  pokemon.getTipos()){
                tipos += tp.getNome();
                tipos += "/";
            }
            Log.d("POKEMONS", pokemon.getNumero() + " - " + pokemon.getNome() + " - " + pokemon.getCategoria() + " - " + pokemon.getFoto() + " - " + pokemon.getIcone() + " - " + tipos);
        }
    }

    // Carrega para a memória APENAS o usuário informado (antes lia a tabela inteira e
    // o último registro "vencia", misturando usuários diferentes do mesmo aparelho).
    private boolean daoUsuario(String login){
        if (login == null) return false;

        Cursor c = BancoDadosSingleton.getInstance().buscar("usuario",
                new String[]{"login","nome","sexo","foto","dtCadastro","xp","nivel"},
                "login = ?", new String[]{login}, "");

        boolean achou = c.moveToFirst();
        if (achou) {
            Usuario u = new Usuario(c.getString(c.getColumnIndex("login")));
            u.setNome(c.getString(c.getColumnIndex("nome")));
            u.setSexo(c.getString(c.getColumnIndex("sexo")));
            u.setFoto(c.getString(c.getColumnIndex("foto")));
            u.setDtCadastro(c.getString(c.getColumnIndex("dtCadastro")));
            u.setXp(c.getInt(c.getColumnIndex("xp")));
            u.setNivel(c.getInt(c.getColumnIndex("nivel")));
            user = u;
        }
        c.close();

        if (achou) {
            daoOvo();
        }
        return achou;
    }

    public static ControladoraFachadaSingleton getInstance(){
        return INSTANCE;
    }

    public Usuario getUsuario(){
        return this.user;
    }

    public PerfilUsuario getPerfilUsuario() {
        return perfilUsuario;
    }

    public void setPerfilUsuario(PerfilUsuario perfilUsuario) {
        this.perfilUsuario = perfilUsuario;
    }

    public InteracaoPokestop interagePokestop(Pokestop p, Date acesso){
        ContentValues valores = new ContentValues();

        valores.put("ultimoAcesso",acesso.getTime());

        Cursor cPokestop = BancoDadosSingleton.getInstance().buscar("interacaopokestop ip",
                new String[]{"ip.idPokestop idPokestop"},
                "ip.idPokestop = ? and ip.loginUsuario = ?",
                new String[]{p.getID(), user.getLogin()}, "");

        if(cPokestop.getCount() > 0){
            BancoDadosSingleton.getInstance().atualizar("interacaopokestop",
                    valores,
                    "idPokestop = ? and loginUsuario = ?",
                    new String[]{p.getID(), user.getLogin()});
        }
        else{
            valores.put("idPokestop",p.getID());
            valores.put("loginUsuario",user.getLogin());
            BancoDadosSingleton.getInstance().inserir("interacaopokestop",valores);
        }

        cPokestop.close();

        InteracaoPokestop interacaoPokestop = new InteracaoPokestop(p, user, acesso);
        p.setDisponivel(false);
        aumentaXp("pokestop");
        sorteiaOvo();
        return interacaoPokestop;
    }

    public InteracaoPokestop getUltimaInteracao(Pokestop p){
        Cursor cPokestop = BancoDadosSingleton.getInstance().buscar("interacaopokestop",
                new String[]{"ultimoAcesso"},
                "idPokestop = ? and loginUsuario = ?",
                new String[]{p.getID(), user.getLogin()}, "");

        Date access = null;
        while(cPokestop.getCount() > 0 && cPokestop.moveToNext()){
            int cAcesso = cPokestop.getColumnIndex("ultimoAcesso");

            access = new Date( cPokestop.getLong(cAcesso)) ;
        }

        cPokestop.close();

        if(access == null)
            p.setDisponivel(true);
        else{
            Date TempoAtual = Calendar.getInstance().getTime();
            long diffMs = TempoAtual.getTime() - access.getTime();
            p.setDisponivel(diffMs >= COOLDOWN_POKESTOP_MS);
        }

        InteracaoPokestop interac = new InteracaoPokestop(p, user, access);
        return interac;
    }

    public List<Pokemon> getPokemons(){
        //extrai do MAP todos os valores pokemon e junta em uma lista ordenada para retornar.
        List<Pokemon> pkmn = new ArrayList<>();

        for (Map.Entry<String, List<Pokemon>> entry : pokemons.entrySet()){
            //add listas de pokemon no final da lista a ser retornada
            pkmn.addAll(entry.getValue());
        }

        //ordena a lista a ser retornada baseado no número do pokemon
        Collections.sort(pkmn, new Comparator<Pokemon>() {
            @Override
            public int compare(Pokemon pk2, Pokemon pk1) {
                if(pk1.getNumero() > pk2.getNumero())
                    return -1;
                else if(pk1.getNumero() < pk2.getNumero())
                    return +1;
                return 0;
            }
        });

        //TODO: apagar testes de impressão lista pokemon
        for(Pokemon p : pkmn){
            Log.d("LISTA_PKMN", "Pokemon: " + p.getNome());
        }

        return pkmn;
    }

    private void verificarFim(AtomicInteger cont, int total, List<Pokestop> lista, AoCarregarPokestopsListener listener) {
        if (cont.incrementAndGet() == total) {
            listener.onPokestopsCarregados(lista);
        }
    }

    public void getPokestops(LatLng localizacaoUsuario, final AoCarregarPokestopsListener listener) {
        final List<Pokestop> listaFinal = new ArrayList<>();

        // Chama o nearby search para obter os lugares
        NearbySearch.run(localizacaoUsuario).addOnSuccessListener(response -> {
            List<Place> places = response.getPlaces();

            if (places.isEmpty()) {
                listener.onPokestopsCarregados(listaFinal);
                return;
            }

            final int total = places.size();
            final AtomicInteger processados = new AtomicInteger(0);

            for (Place place : places) {
                Pokestop pokestop = new Pokestop(place.getId(), place.getName());
                if (place.getLatLng() != null) {
                    pokestop.setlat(place.getLatLng().latitude);
                    pokestop.setlong(place.getLatLng().longitude);
                }

                // Chama a lógica de banco de dados
                processarBancoDeDados(pokestop);

                // Busca a imagem dos lugares usando o metodo especifico
                getPlaceImage(pokestop, place.getPhotoMetadatas(), () -> {
                    listaFinal.add(pokestop);
                    // Quando terminarem (fotos inclusas ou falhas), avisa o listener
                    if (processados.incrementAndGet() == total) {
                        listener.onPokestopsCarregados(listaFinal);
                    }
                });
            }
        }).addOnFailureListener(e -> Log.e("PGO", "Erro na busca: " + e.getMessage()));
    }

    // Metodo auxiliar apenas para limpar o getPokestops
    private void processarBancoDeDados(Pokestop pokestop) {
        Cursor c = BancoDadosSingleton.getInstance().buscar("pokestop",
                new String[]{"disponivel"}, "idPokestop = '" + pokestop.getID() + "'", "");

        if (c.getCount() > 0 && c.moveToNext()) {
            pokestop.setDisponivel(c.getInt(c.getColumnIndex("disponivel")) != 0);
        } else {
            ContentValues v = new ContentValues();
            v.put("idPokestop", pokestop.getID());
            v.put("latitude", pokestop.getlat());
            v.put("longitude", pokestop.getlongi());
            v.put("disponivel", true);
            BancoDadosSingleton.getInstance().inserir("Pokestop", v);
            pokestop.setDisponivel(true);
        }
        c.close();
    }

    // Recuperar imagem do local usando sdk places
    private void getPlaceImage(Pokestop pokestop, List<PhotoMetadata> photoMetadatas, Runnable onFinish) {
        if (photoMetadatas == null || photoMetadatas.isEmpty()) {
            Log.d("FOTO", "Sem foto para: " + pokestop.getNome());
            onFinish.run();
            return;
        }

        PlacesClient placesClient = Places.createClient(MyApp.getAppContext());
        FetchPhotoRequest photoRequest = FetchPhotoRequest.builder(photoMetadatas.get(0))
                .setMaxWidth(500)
                .setMaxHeight(300)
                .build();

        placesClient.fetchPhoto(photoRequest)
                .addOnSuccessListener(response -> {
                    pokestop.setFoto(response.getBitmap());
                    onFinish.run();
                })
                .addOnFailureListener(e -> {
                    Log.e("FOTO", "Erro ao baixar foto: " + e.getMessage());
                    onFinish.run();
                });
    }

    public Aparecimento[] getAparecimentos(){
        return this.aparecimentos;
    }

    protected List<Tipo> getTipos(){
        return tiposPokemon;
    }

    public List<Doce> getDoces() {
        return doces;
    }

    public List<Ovo> getOvos(){ return ovos; }

    public void removeOvo(int i){
        Ovo o = ovos.get(i);
        ovos.remove(o);
    }

    public Pokemon getPokemonOvo(int idOvo){
        Pokemon p = null;
        Cursor c = BancoDadosSingleton.getInstance().buscar("pokemon p, ovo o",new String[]{"p.idPokemon idPokemon","p.nome nome","p.categoria categoria","p.foto foto","p.icone icone","p.idDoce idDoce", "p.idPokemonBase idPokemonBase"},"o.idPokemon = p.idPokemon AND o.idOvo = '"+idOvo+"'","");
        while (c.moveToNext()) {
            int idP = c.getColumnIndex("idPokemon");
            int name = c.getColumnIndex("nome");
            int cat = c.getColumnIndex("categoria");
            int foto = c.getColumnIndex("foto");
            int icone = c.getColumnIndex("icone");
            int idDoce = c.getColumnIndex("idDoce");
            int idPokemonBase = c.getColumnIndex("idPokemonBase");

            p = new Pokemon(c.getInt(idP),c.getString(name),c.getString(cat),c.getInt(foto),c.getInt(icone),c.getInt(idDoce),c.getInt(idPokemonBase),this);
            Log.i("GET", "Nome: " + p.getNome());

        }
        c.close();
        return p;
    }

    public void setIncubado(int idOvo,int incubado){
        ContentValues valores = new ContentValues();
        valores.put("incubado",incubado);
        BancoDadosSingleton.getInstance().atualizar("ovo",valores,"idOvo = '"+idOvo+"'");
    }

    public void setExibido(int idOvo,int exibido){

        ContentValues valores = new ContentValues();
        valores.put("exibido",exibido);
        BancoDadosSingleton.getInstance().atualizar("ovo",valores,"idOvo = '"+idOvo+"'");

    }

    public void setChocado(int idOvo,int chocado){

        ContentValues valores = new ContentValues();
        valores.put("chocado",chocado);
        BancoDadosSingleton.getInstance().atualizar("ovo",valores,"idOvo = '"+idOvo+"'");

    }

    public void setKmAndado(int idOvo, double kmAndado){
        ContentValues valores = new ContentValues();
        valores.put("kmAndado",kmAndado);
        BancoDadosSingleton.getInstance().atualizar("ovo",valores,"idOvo = '"+idOvo+"'");
    }

    public int quantidadeOvosIncubado(){
        int quantidadeOvosIncubado = 0;
        for(int i = 0; i < ovos.size(); i++){
            if(ovos.get(i).getIncubado() == 1) quantidadeOvosIncubado++;
        }
        Log.i("INCUBADO:","Quantidade de ovos incubados: "+ quantidadeOvosIncubado);
        return quantidadeOvosIncubado;
    }

    // Sorteia só o TIPO do ovo (C/I/R/L = 2/5/7/10 km). O animal NÃO é escolhido aqui:
    // ele é sorteado ao chocar (ver sortearAnimalDoOvo). A coluna ovo.idPokemon fica com 0
    // (sem uso; o SQLite do app não liga PRAGMA foreign_keys, então a FK legada não é aplicada).
    public void sorteiaOvo(){
        final int SEM_ANIMAL = 0;

        //obtem hora atual
        Map<String,String> tempo = TimeUtil.getHoraMinutoSegundoDiaMesAno();

        Log.d("TEMPO",tempo.get("hora") +":"+tempo.get("minuto")+":"+tempo.get("segundo")+" - "+tempo.get("dia")+"/"+tempo.get("mes")+"/"+tempo.get("ano")+" "+tempo.get("timezone"));

        //obtem valores a serem usado no critério de lendários
        int numIntSorteado = RandomUtil.randomIntInRange(1,101);
        int numIntSorteado2 = RandomUtil.randomIntInRange(1,101);
        int somaMinSegAtual = (Integer.parseInt(tempo.get("minuto")) + Integer.parseInt(tempo.get("segundo")));

        Log.d("SORTEIO_OVO","NumInt: " + numIntSorteado + " NumInt2: " + numIntSorteado2 + " SomaMinSeg: " + somaMinSegAtual);

        //sorteia OVO LENDÁRIO (10 km)
        if(numIntSorteado % 2 == 0 && numIntSorteado2 % 2 == 0 && somaMinSegAtual % 2 != 0){
            cadastraOvo(SEM_ANIMAL, "L", 0, 0, 0, 0);
            Log.d("SORTEIO_OVO","Ovo LENDÁRIO (10 km)");

            //sorteia OVO RARO (7 km)
        } else if(numIntSorteado % 2 != 0 && numIntSorteado2 % 2 != 0){
            cadastraOvo(SEM_ANIMAL, "R", 0, 0, 0, 0);
            Log.d("SORTEIO_OVO","Ovo RARO (7 km)");

            //sorteia OVO INCOMUM (5 km)
        } else if(numIntSorteado <= 35){
            cadastraOvo(SEM_ANIMAL, "I", 0, 0, 0, 0);
            Log.d("SORTEIO_OVO","Ovo INCOMUM (5 km)");

            //Sorteia OVO COMUM (2 km)
        } else {
            cadastraOvo(SEM_ANIMAL, "C", 0, 0, 0, 0);
            Log.d("SORTEIO_OVO","Ovo COMUM (2 km)");
        }
    }

    public void setTodosAnimais(List<Animal> animais) {
        this.todosAnimais = animais != null ? animais : new ArrayList<Animal>();
        Log.d("SORTEIO_OVO", "Animais disponíveis para ovos: " + this.todosAnimais.size());
    }

    // REGRA TEMPORÁRIA (2026-11): qualquer animal cadastrado pode sair do ovo, em sorteio
    // uniforme. Será substituída por uma regra própria (ex.: região do jogador/raridade).
    // Retorna null se a lista de animais ainda não foi carregada (ex.: app aberto sem internet).
    public Animal sortearAnimalDoOvo() {
        List<Animal> animais = todosAnimais;
        if (animais.isEmpty()) {
            Log.w("SORTEIO_OVO", "Lista de animais vazia: o ovo espera para chocar");
            return null;
        }
        Animal sorteado = animais.get(RandomUtil.randomIntInRange(0, animais.size()));
        Log.d("SORTEIO_OVO", "Animal do ovo (regra temporária: qualquer animal): " + sorteado.getNomePopular());
        return sorteado;
    }

    // Choca o ovo: sorteia o animal, registra no backend como captura de origem "ovo"
    // (aparece no Diário) e dá o XP de chocar. Sem doces e sem tabela local de Pokémon.
    // Retorna null quando ainda não há animal para sortear: o ovo continua esperando.
    public Animal chocarOvo(int idOvo, android.location.Location local) {
        final Animal animal = sortearAnimalDoOvo();
        if (animal == null) return null;

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("idAnimal", animal.getId());
        body.addProperty("latitude", local != null ? local.getLatitude() : 0);
        body.addProperty("longitude", local != null ? local.getLongitude() : 0);
        body.addProperty("origem", "ovo");

        RetrofitClient.getApi().registrarCaptura(body).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (response.isSuccessful()) {
                    idsAnimaisCapturados.add(animal.getId());
                    Log.d("OVO", "Ovo " + idOvo + " chocou " + animal.getNomePopular() + " (registrado no servidor)");
                } else {
                    Log.e("OVO", "Erro ao registrar " + animal.getNomePopular() + " chocado: " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                Log.e("OVO", "Falha ao registrar " + animal.getNomePopular() + " chocado: " + t.getMessage());
            }
        });

        aumentaXp("choca");
        return animal;
    }


    // LEGADO (2026-11): sorteio local de Pokémon, desligado. O mapa usa sorteiaAparecimentosAnimais.
    public void sorteiaAparecimentos(double LatMin, double LatMax, double LongMin, double LongMax){

        int tamComum = pokemons.get("C").size();
        int tamIncomum = pokemons.get("I").size();
        int tamRaro = pokemons.get("R").size();
        int tamLendario = pokemons.get("L").size();

        Log.d("SORTEIO","C: " + tamComum + " I: "+ tamIncomum + " R: "+ tamRaro + " L: " + tamLendario);

        int contAparecimentos = 0;
        int totalComuns = 0; //valor a ser definido no sorteio de lendário

        int min = 0;
        int max;

        //obtem hora atual
        Map<String,String> tempo = TimeUtil.getHoraMinutoSegundoDiaMesAno();
        Log.d("TEMPO",tempo.get("hora") +":"+tempo.get("minuto")+":"+tempo.get("segundo")+" - "+tempo.get("dia")+"/"+tempo.get("mes")+"/"+tempo.get("ano")+" "+tempo.get("timezone"));

        //obtem valores a serem usado no critério de lendários
        int numIntSorteado = RandomUtil.randomIntInRange(1,101);
        int numIntSorteado2 = RandomUtil.randomIntInRange(1,101);
        int somaMinSegAtual = (Integer.parseInt(tempo.get("minuto")) + Integer.parseInt(tempo.get("segundo")));

        Log.d("SORTEIO","NumInt: " + numIntSorteado + " NumInt2: " + numIntSorteado2 + " SomaMinSeg: " + somaMinSegAtual);

        //define se sorteia lendário
        if(!sorteouLendario && numIntSorteado % 2 == 0 && numIntSorteado2 % 2 == 0 && somaMinSegAtual % 2 != 0){
            sorteouLendario = true;
            totalComuns = 5;

            //sorteia pokemons lendário
            for(int i = 0; i < 1; i++){
                max = tamLendario;
                int sorteio = RandomUtil.randomIntInRange(min,max);

                Aparecimento ap = new Aparecimento();
                ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
                ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
                ap.setPokemon(pokemons.get("L").get(sorteio));

                this.aparecimentos[contAparecimentos] = ap;
                Log.d("SORTEIO","LENDÁRIO: " + aparecimentos[contAparecimentos].getPokemon().getNome());

                contAparecimentos++;
            }
        }
        else{
            sorteouLendario = false;
            totalComuns = 6;
        }

        //sorteia pokemons comuns
        for(int i = 0; i < totalComuns; i++){
            max = tamComum;
            int sorteio = RandomUtil.randomIntInRange(min,max);

            Aparecimento ap = new Aparecimento();
            ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
            ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
            ap.setPokemon(pokemons.get("C").get(sorteio));

            this.aparecimentos[contAparecimentos] = ap;
            Log.d("SORTEIO","COMUM: " + aparecimentos[contAparecimentos].getPokemon().getNome());

            contAparecimentos++;
        }

        //sorteia pokemons incomuns
        for(int i = 0; i < 3; i++){
            max = tamIncomum;
            int sorteio = RandomUtil.randomIntInRange(min,max);

            Aparecimento ap = new Aparecimento();
            ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
            ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
            ap.setPokemon(pokemons.get("I").get(sorteio));

            this.aparecimentos[contAparecimentos] = ap;
            Log.d("SORTEIO","INCOMUM: " + aparecimentos[contAparecimentos].getPokemon().getNome());

            contAparecimentos++;
        }

        //sorteia pokemons raros
        for(int i = 0; i < 1; i++){
            max = tamRaro;
            int sorteio = RandomUtil.randomIntInRange(min,max);

            Aparecimento ap = new Aparecimento();
            ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
            ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
            ap.setPokemon(pokemons.get("R").get(sorteio));

            this.aparecimentos[contAparecimentos] = ap;
            Log.d("SORTEIO","RARO: " + aparecimentos[contAparecimentos].getPokemon().getNome());

            contAparecimentos++;
        }
    }

    // Spawn de animais reais: o sorteio (habitat + VGI + hábito/horário) é feito no backend
    // (logs "[SPAWN]" no servidor explicam quem foi puxado/rejeitado e por quê); aqui o app só
    // espalha os animais recebidos dentro da caixa ao redor do jogador.
    // SÍNCRONO: rode fora da UI thread (é chamado pela thread de sorteio do MapActivity).
    public void sorteiaAparecimentosAnimais(double latitude, double longitude,
                                           double latMin, double latMax, double lngMin, double lngMax) {
        // Garante o rótulo "novo"/"conhecido" correto antes do 1º animal aparecer no mapa
        carregarCapturasSeNecessario();

        Log.d("SPAWN_ANIMAIS", "Pedindo spawn em " + latitude + ", " + longitude
                + " | caixa lat[" + latMin + ", " + latMax + "] lng[" + lngMin + ", " + lngMax + "]");
        try {
            retrofit2.Response<List<Animal>> response =
                    RetrofitClient.getApi().getSpawnAnimais(latitude, longitude, 10).execute();

            if (!response.isSuccessful() || response.body() == null) {
                Log.e("SPAWN_ANIMAIS", "Erro na resposta: HTTP " + response.code() + " (mantidos os animais anteriores)");
                return;
            }

            List<Animal> animais = response.body();
            if (animais.isEmpty()) {
                Log.w("SPAWN_ANIMAIS", "Lista vazia: nenhum animal para esta região/horário. "
                        + "Veja os logs [SPAWN] do backend para o motivo de cada rejeição.");
            }

            Aparecimento[] novos = new Aparecimento[animais.size()];
            for (int i = 0; i < animais.size(); i++) {
                Animal a = animais.get(i);
                Aparecimento ap = new Aparecimento();
                ap.setAnimal(a);
                ap.setLatitude(RandomUtil.randomDoubleInRange(latMin, latMax));
                ap.setLongitude(RandomUtil.randomDoubleInRange(lngMin, lngMax));
                novos[i] = ap;

                boolean temFoto = a.getFotoUrl() != null && !a.getFotoUrl().isEmpty();
                Log.d("SPAWN_ANIMAIS", "Puxado do backend: " + a.getNomePopular() + " (id " + a.getId()
                        + ", hábito " + a.getHabito() + ") -> posição " + ap.getLatitude() + ", " + ap.getLongitude()
                        + (temFoto ? "" : " | SEM FOTO: usará o marcador padrão"));
            }
            aparecimentos = novos;
            Log.d("SPAWN_ANIMAIS", "Total no mapa: " + novos.length + " animal(is)");
        } catch (java.io.IOException e) {
            Log.e("SPAWN_ANIMAIS", "Falha de conexão: " + e.getMessage() + " (mantidos os animais anteriores)");
        }
    }

    // GET /api/capturas/me síncrono, só enquanto ainda não carregou com sucesso
    private void carregarCapturasSeNecessario() {
        if (capturasCarregadas) return;
        try {
            retrofit2.Response<List<Animal>> response = RetrofitClient.getApi().getMinhasCapturas().execute();
            if (response.isSuccessful() && response.body() != null) {
                setIdsAnimaisCapturados(response.body());
            } else {
                Log.e("CAPTURAS", "Erro ao carregar capturas: HTTP " + response.code() + " (tenta de novo no próximo ciclo)");
            }
        } catch (java.io.IOException e) {
            Log.e("CAPTURAS", "Falha ao carregar capturas: " + e.getMessage() + " (tenta de novo no próximo ciclo)");
        }
    }

    // Substitui o conjunto pela lista completa vinda do servidor (Diário ou carga inicial)
    public void setIdsAnimaisCapturados(List<Animal> capturados) {
        synchronized (idsAnimaisCapturados) {
            idsAnimaisCapturados.clear();
            for (Animal a : capturados) idsAnimaisCapturados.add(a.getId());
        }
        capturasCarregadas = true;
        Log.d("CAPTURAS", "Espécies já obtidas: " + capturados.size());
    }

    public boolean isCapturasCarregadas() {
        return capturasCarregadas;
    }

    public boolean isAnimalCapturado(int idAnimal) {
        return idsAnimaisCapturados.contains(idAnimal);
    }

    // POST /api/capturas da captura feita no mapa. O servidor valida a área, grava e devolve o
    // progresso (xp/nivel), que passa a ser o valor local.
    public void registrarCapturaAnimal(Aparecimento ap) {
        final Animal animal = ap.getAnimal();
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("idAnimal", animal.getId());
        body.addProperty("latitude", ap.getLatitude());
        body.addProperty("longitude", ap.getLongitude());

        RetrofitClient.getApi().registrarCaptura(body).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                com.google.gson.JsonObject resposta = response.body();
                if (response.isSuccessful() && resposta != null) {
                    idsAnimaisCapturados.add(animal.getId());
                    if (resposta.has("progresso") && resposta.get("progresso").isJsonObject()) {
                        com.google.gson.JsonObject p = resposta.getAsJsonObject("progresso");
                        salvarProgressoLocal(p.get("xp").getAsInt(), p.get("nivel").getAsInt());
                    }
                    Log.d("CAPTURAS", animal.getNomePopular() + " registrado no servidor");
                } else {
                    Log.e("CAPTURAS", "Servidor recusou a captura de " + animal.getNomePopular() + ": HTTP " + response.code());
                    Toast.makeText(MyApp.getAppContext(), "Não foi possível registrar a captura de "
                            + animal.getNomePopular() + " no servidor.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                Log.e("CAPTURAS", "Falha ao registrar a captura de " + animal.getNomePopular() + ": " + t.getMessage());
                Toast.makeText(MyApp.getAppContext(), "Sem conexão: a captura de "
                        + animal.getNomePopular() + " não foi registrada.", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Marca o usuário como o único com sessão aberta e o carrega na memória.
    // A fonte da verdade da sessão é o TokenManager; a coluna temSessao é mantida coerente.
    private void abrirSessao(String login){
        ContentValues nao = new ContentValues();
        nao.put("temSessao", "NAO");
        BancoDadosSingleton.getInstance().atualizar("usuario", nao, "temSessao = ?", new String[]{"SIM"});

        ContentValues sim = new ContentValues();
        sim.put("temSessao", "SIM");
        BancoDadosSingleton.getInstance().atualizar("usuario", sim, "login = ?", new String[]{login});

        daoUsuario(login);
    }

    // Chamado após TODO login/cadastro online: espelha o perfil da nuvem no SQLite.
    // Upsert: se o usuário já existe no aparelho, só atualiza (capturas, doces e ovos dele
    // continuam lá); se não existe, cria. Nunca apaga dados de outros usuários.
    public synchronized void salvarUsuarioDaNuvem(String login, PerfilUsuario perfil){
        this.perfilUsuario = perfil;

        ContentValues valores = new ContentValues();
        valores.put("nome", perfil.getNome() != null ? perfil.getNome() : login);
        valores.put("sexo", perfil.getSexo() != null ? perfil.getSexo() : "M");
        valores.put("foto", perfil.getFoto() != null ? perfil.getFoto() : "");
        valores.put("nivel", perfil.getNivel());
        valores.put("xp", perfil.getXp());
        if (perfil.getDtCadastro() != null) valores.put("dtCadastro", perfil.getDtCadastro());

        int atualizados = BancoDadosSingleton.getInstance().atualizar("usuario", valores, "login = ?", new String[]{login});

        if (atualizados == 0) {
            if (perfil.getDtCadastro() == null) {
                Map<String,String> ts = TimeUtil.getHoraMinutoSegundoDiaMesAno();
                valores.put("dtCadastro", ts.get("dia")+"/"+ts.get("mes")+"/"+ts.get("ano")+" "+ts.get("hora")+":"+ts.get("minuto")+":"+ts.get("segundo"));
            }
            valores.put("login", login);
            valores.put("temSessao", "NAO");
            BancoDadosSingleton.getInstance().inserir("usuario", valores);
        }

        abrirSessao(login);
    }

    // Encerra a sessão: limpa a memória e os tokens. Os dados locais do usuário (capturas,
    // doces, ovos e relatos VGI pendentes) ficam guardados para o próximo login dele.
    public synchronized boolean logoutUser(){
        if (user != null) {
            ContentValues valores = new ContentValues();
            valores.put("temSessao", "NAO");
            BancoDadosSingleton.getInstance().atualizar("usuario", valores, "login = ?", new String[]{user.getLogin()});
        }

        user = null;
        perfilUsuario = null;
        ovos.clear();
        aparecimentos = new Aparecimento[0];
        idsAnimaisCapturados.clear();
        capturasCarregadas = false;

        TokenManager.limpar(MyApp.getAppContext());
        return true;
    }

    public void cadastraOvo(int idPokemon, String idTipoOvo, int incubado, int chocado, int exibido, int KmAndado){
        if(this.ovos.size() < 9){

            ContentValues valores = new ContentValues();
            valores.put("idPokemon", idPokemon);
            valores.put("idTipoOvo", idTipoOvo);
            valores.put("loginUsuario", user.getLogin());
            valores.put("incubado", incubado);
            valores.put("chocado", chocado);
            valores.put("exibido", exibido);
            valores.put("KmAndado", KmAndado);

            BancoDadosSingleton.getInstance().inserir("ovo", valores);

            //adiciona na lista de ovos
            daoOvo();
        }
    }

    // Há sessão quando existe refresh token salvo E o dono dele está no SQLite.
    // Não depende de internet nem de senha: é isso que permite abrir o jogo offline.
    public boolean temSessao(){
        if (!TokenManager.temSessao(MyApp.getAppContext())) return false;
        return daoUsuario(TokenManager.getLogin(MyApp.getAppContext()));
    }

    public Pokemon convertPokemonSerializableToObject(Pokemon pkmn) {
        //obtem lista de pokemons da controladora geral
        List<Pokemon> listPkmn = this.getPokemons();
        Pokemon pkmnAux = null;

        //procura na lista de pokemons da controladora o pokemon parâmetro.
        for (Pokemon pokemon : listPkmn) {
            if (pokemon.getNumero() == pkmn.getNumero()) {
                pkmnAux = pokemon;
                break;
            }
        }

        return pkmnAux;
    }

    public boolean aumentaXp(String evento) {
        final int xpRecebido = getXpEvento(evento);
        final int nivelAtual = getUsuario().getNivel();
        final int xpAtual = getUsuario().getXp();
        final int xpMax = xpMaximo(nivelAtual);
        int xpFinal = xpAtual, nivelFinal = nivelAtual;

        if((xpAtual + xpRecebido) >= xpMax) {
            xpFinal = (xpAtual + xpRecebido) - xpMax;
            nivelFinal++;

            if(nivelFinal > 40) {
                nivelFinal = 40;
                xpFinal = xpMaximo(nivelFinal);
            }

            getUsuario().setNivel(nivelFinal);
        } else {
            xpFinal = xpAtual + xpRecebido;
        }

        getUsuario().setXp(xpFinal);

        ContentValues valores = new ContentValues();
        valores.put("nivel", nivelFinal);
        valores.put("xp", xpFinal);

        int count = BancoDadosSingleton.getInstance().atualizar("usuario", valores, "login = ?", new String[]{getUsuario().getLogin()});

        if(count == 1) {
            Toast.makeText(MyApp.getAppContext(), "Você ganhou " + xpRecebido + " de XP", Toast.LENGTH_SHORT).show();

            if(nivelFinal > nivelAtual) {
                Toast.makeText(MyApp.getAppContext(), "Parabéns! Você avançou para o nível " + nivelFinal, Toast.LENGTH_SHORT).show();
            }
        }

        // Só informa a ação; o backend decide o XP. A captura é contabilizada pelo POST /api/capturas.
        if (evento.equals("pokestop") || evento.equals("choca")) {
            enviarAcaoXp(evento);
        }

        return count == 1;
    }

    // PATCH /api/usuarios/me/progresso com {"acao": evento}; a resposta do servidor é a fonte da verdade
    private void enviarAcaoXp(final String evento) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("acao", evento);

        RetrofitClient.getApi().atualizarProgresso(body).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                com.google.gson.JsonObject usuario = response.body();
                if (response.isSuccessful() && usuario != null && usuario.has("xp") && usuario.has("nivel")) {
                    salvarProgressoLocal(usuario.get("xp").getAsInt(), usuario.get("nivel").getAsInt());
                } else {
                    Log.e("XP", "Erro ao enviar ação '" + evento + "': " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                Log.e("XP", "Falha ao enviar ação '" + evento + "': " + t.getMessage());
            }
        });
    }

    // Sobrescreve o progresso local (memória + SQLite) com os valores do servidor
    private void salvarProgressoLocal(int xp, int nivel) {
        if (getUsuario() == null) return;

        getUsuario().setXp(xp);
        getUsuario().setNivel(nivel);

        ContentValues valores = new ContentValues();
        valores.put("xp", xp);
        valores.put("nivel", nivel);
        BancoDadosSingleton.getInstance().atualizar("usuario", valores, "login = ?", new String[]{getUsuario().getLogin()});
    }

    public int xpMaximo(int nivelUsuario) {
        return nivelUsuario*1000;
    }

    public int getXpEvento(String evento) {
        switch(evento) {
            case "captura":
                return 20;
            case "evolui":
                return 200;
            case "pokestop":
                return 50;
            case "choca":
                return 100;
            default:
                return 0;
        }
    }
}
