/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;


import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.Map;

/**
 *
 * @author franmolsan
 */
public class Coach extends AgenteDrone {
    //final static String TIPO = "LISTENER";
    // atributos heredados del AgenteDrone:
    
    // protected YellowPages yp;
    // protected String estado, servicio, mundo;
    // protected String worldManager, convID;
    // protected boolean hayError;
    // protected ACLMessage in, out;
    // protected Map2DGrayscale mapa;
    private ArrayList<String> buscadores = new ArrayList<String>();
    private ArrayList<String> sensoresBuscadores = new ArrayList<String>();
    private ArrayList<String> sensoresRescatador = new ArrayList<String>();
    private String rescatador = "";
    private String listener = "";
    private ArrayList<ArrayList<Integer>> matrizPosiciones = new ArrayList<>();
    
    @Override
    public void setup() {
        super.setup();
        buscadores.add("NobitaSinGafas1");
        buscadores.add("OvejaOscar1");
        buscadores.add("DoraLaExploradora1");
        rescatador = "EduardoManosTijeras1";
        listener = "Dumbo1";
        
        sensoresBuscadores.add("alive");
        sensoresBuscadores.add("energy");
        
        // luego añadiremos otros sensores en función del tamaño del mapa
        
        sensoresRescatador.add("alive");
        sensoresRescatador.add("energy");
    }
    
    @Override
    public void plainExecute() {
        plainWithErrors();
    }


    public void plainWithErrors() {
        // Basic iteration
        switch (estado.toUpperCase()) {
            case "CHECKIN-LARVA":
                checkInLarva();
                break;
            case "SUBSCRIBE-WM":
                suscribirseWM();
                estado = "PROCESS-MAP";
                break;
            case "PROCESS-MAP":
                procesarMapa();
                calcularParametrosSegunWorldSize();
                break;
            case "SETUP-COMUNICADOR":
                setupComunicador();
                estado = "SETUP-BUSCADORES";
                break;
            case "SETUP-BUSCADORES":
                setupBuscadores();
                estado = "SETUP-RESCATADOR";
                break;
            case "SETUP-RESCATADOR":
                setupRescatador();
                estado = "GESTIONAR-COMPRA";
                break;
            case "GESTIONAR-COMPRA":
                obtenerYPTiendas();
                estado = "COMPRAR-SENSORES";
                break;
            case "COMPRAR-SENSORES":
                comprarSensores();
                estado = "REALIZAR-LOGIN";
                break;
            case "REALIZAR-LOGIN":
                realizarLoginDrones();
                estado = "DESPERTAR-AWACS";
                break;
            case "DESPERTAR-AWACS":
                despertarAWACS();
                estado = "ESPERAR-TODOS-RESCATADOS";
                break;
            case "ESPERAR-TODOS-RESCATADOS":
                in = blockingReceive();
                if (in.getContent().equals("dead")){
                    gestionarMuerte();
                }
                
                if (in.getContent().equals("turnOffCompleted") && 
                    in.getSender().getName().contains(rescatador)){
                    estado = "CANCEL-BUSCADORES";
                }
                break;
            case "CANCEL-BUSCADORES":
                cancelarBuscadores();
                estado = "CANCEL-COMUNICADOR";
                break;
            case "CANCEL-COMUNICADOR":
                cancelarComunicador();
                estado = "CANCEL-WM";
                break;
            case "CANCEL-WM":
                cancelarWM();
                estado = "CHECKOUT-LARVA";
                break;
            case "CHECKOUT-LARVA":
                checkOutLarva();
                break;
            case "EXIT":
                exit();
                break;
        }
    }
    
    @Override
    protected void suscribirseWM(){Info("Retrieve who is my WM");
        // First update Yellow Pages
        in = obtenerYP(_identitymanager); // As seen oon slides
        Info("YP obtenidas");
        hayError = in.getPerformative() != ACLMessage.INFORM;
        if (hayError) {
            Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                    + " Query YellowPages failed due to " + getDetailsLARVA(in));
            estado = "CHECKOUT-LARVA";
        }
        else{
            yp = new YellowPages();
            yp.updateYellowPages(in);
            // It might be the case that YP are right but we dont find an appropriate service for us, then leave
            if (yp.queryProvidersofService(servicio).isEmpty()) {
                Info("\t" + "There is no agent providing the service " + servicio);
                estado = "CHECKOUT-LARVA";
            }
            // Choose one of the available service providers, i.e., the first one
            worldManager = yp.queryProvidersofService(servicio).iterator().next();

            // Now it is time to start the game and turn on the lights within a given world
            in = suscribirseA(worldManager, new JsonObject().add("problem", mundo).toString());
            hayError = in.getPerformative() != ACLMessage.INFORM;
            if (hayError) {
                Info(ACLMessage.getPerformative(in.getPerformative())
                        + " Could not open a session with "
                        + worldManager + " due to " + getDetailsLARVA(in));
                estado = "CHECKOUT-LARVA";
            }
        }
        // Keep the Conversation ID and spread it amongs the team members
        convID = in.getConversationId();
        Info(convID);
    }
        
    
    private void despertarComunicador(){   
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(new JsonObject().add("idListener", getAID().toString()).toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.QUERY_IF);
        out.addReceiver(new AID(listener, AID.ISLOCALNAME));
        
        send(out);
    }
    
    private void obtenerYPTiendas(){
        in = obtenerYP(_identitymanager); // As seen oon slides
        yp.updateYellowPages(in);
    }
    
    private void despertarBuscadores(){   
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(new JsonObject().add("idListener", getAID().toString()).toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.QUERY_IF);
        for (int i=0; i<buscadores.size(); i++){
            out.addReceiver(new AID(buscadores.get(i), AID.ISLOCALNAME));
        }
        send(out);
    }
    
    private void despertarRescatador(){   
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(new JsonObject().add("idListener", getAID().toString()).toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.QUERY_IF);
        out.addReceiver(new AID(rescatador, AID.ISLOCALNAME));
        
        send(out);
    }
    
    private void procesarMapa(){
        System("Save map of world " + mundo);
        // Examines the content of the message from server
        JsonObject jscontent = getJsonContentACLM(in);
        if (jscontent.names().contains("map")) {
            JsonObject jsonMapFile = jscontent.get("map").asObject();
            String mapfilename = jsonMapFile.getString("filename", "nonamefound");
            Info("Found map " + mapfilename);
            mapa = new Map2DGrayscale();
            if (mapa.fromJson(jsonMapFile)) {
                Info("Map " + mapfilename + "( " + mapa.getWidth() + "cols x" + mapa.getHeight()
                        + "rows ) saved on disk (project's root folder) and ready in memory");
                Info("Sampling three random points for cross-check:");
                int px, py;
                for (int ntimes = 0; ntimes < 3; ntimes++) {
                    px = (int) (Math.random() * mapa.getWidth());
                    py = (int) (Math.random() * mapa.getHeight());
                    Info("\tX: " + px + ", Y:" + py + " = " + mapa.getLevel(px, py));
                }
                estado = "SETUP-COMUNICADOR";
            } else {
                Info("\t" + "There was an error processing and saving the image ");
                estado = "CANCEL-WM";
            }
        } else {
            Info("\t" + "There is no map found in the message");
            estado = "CANCEL-WM";
        }
    }
    
    private void setupComunicador(){
        despertarComunicador();
        esperarSetupComunicador();
        Info("Setup del comunicador completo");
    }
    
    private void setupBuscadores(){
        despertarBuscadores();
        esperarSetupBuscadores();
        Info("Setup de los buscadores completo");
    }
        
    
    private void setupRescatador(){
        despertarRescatador();     
        esperarSetupRescatador();
        Info("Setup del rescatador completo");
    }
    
    private void despertarAWACS(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(new JsonObject().toString());
        out.setProtocol("REGULAR");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID("AWACS", AID.ISLOCALNAME));
        send(out);
        
        try {
            Thread.sleep(5000);
        }
        catch (Exception ex){
            Info("Error en AWACS: " + ex);
        };
    }
    
    private void comprarSensores(){
        Object tiendas[];
        JsonValue resultado;
        tiendas  = yp.queryProvidersofService(convID).toArray();
        for (int i=0; i<sensoresRescatador.size(); i++){
            resultado = obtenerMejorPrecioParaSensor(tiendas, sensoresRescatador.get(i));
            enviarCompraDrone(resultado,rescatador);
            in = blockingReceive();
            Info("Compra realziada: " + in.getContent());
        }
        
        finalizarCompra(rescatador);

        for(int i=0;i<buscadores.size();i++){
            tiendas = yp.queryProvidersofService(convID).toArray();
            for (int j=0; j<sensoresRescatador.size(); j++){
                resultado = obtenerMejorPrecioParaSensor(tiendas, sensoresBuscadores.get(j));
                enviarCompraDrone(resultado, buscadores.get(i));
                in = blockingReceive();
                Info(in.getContent());
            }
            finalizarCompra(buscadores.get(i));
        }

    }
    
    private void esperarCancelRescatador(){
        in = blockingReceive();
        hayError = in.getPerformative() != ACLMessage.INFORM;
        if (hayError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " Could not open a session with "
                    + worldManager + " due to " + getDetailsLARVA(in));
            estado = "CHECKOUT-LARVA";
        }
    }
    
    private void cancelarBuscadores(){
        informarCancelacionABuscadores();
        esperarCancelBuscadores();
        Info("Buscadores Cancelados");
    }
    
    private void cancelarComunicador(){
        informaCancelacionAComunicador();
        esperarCancelComunicador();
        Info("Comunicador cancelados");
    }
    
    private void cancelarWM(){
        Info("Closing the game");
        in = enviarCancelA(worldManager);

        //apagar AWACS para que acabe bien el programa
        //in = enviarCancelA("AWACS"); 
    }
        
    private void informarCancelacionABuscadores(){
        JsonObject msg = new JsonObject();
        msg.add("action", "turnOff");
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.INFORM);
        for (int i=0; i<buscadores.size(); i++){
            out.addReceiver(new AID(buscadores.get(i), AID.ISLOCALNAME));
        }
        send(out);
    }
    
    private void informaCancelacionAComunicador(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent("turnOffListener");
        out.setProtocol("REGULAR");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(listener, AID.ISLOCALNAME));
        
        send(out);
    }
    
    private void esperarSetupBuscadores(){
        for (int i=0; i<buscadores.size(); i++){
            in = blockingReceive();
            hayError = (in.getPerformative() != ACLMessage.INFORM);
            if (hayError) {
                Info("\t" + "ERROR");
                estado = "EXIT";
                break;
            }
            else{
               Info("MSG: " + in.getContent());
            }
        }
    }
    
    private void esperarSetupComunicador(){
        in = blockingReceive();
//        hayError = (in.getPerformative() != ACLMessage.INFORM);
//            if (hayError) {
//                Info("\t" + "ERROR");
//                estado = "EXIT";
//            }
//            else{
//                Info("MSG: " + in.getContent());
//            }
    }
    
    private void esperarConfirmacionCompraBuscadores(){
        for (int i=0; i<buscadores.size(); i++){
            in = blockingReceive();
            hayError = (in.getPerformative() != ACLMessage.INFORM);
                if (hayError) {
                    Info("\t" + "ERROR");
                    estado = "EXIT";
                    break;
                }
                else{
                   Info("MSG: " + in.getContent());
                }
        }
    }
    
    private void esperarSetupRescatador(){
        in = blockingReceive();
//        hayError = (in.getPerformative() != ACLMessage.INFORM);
//            if (hayError) {
//                Info("\t" + "ERROR");
//                estado = "EXIT";
//            }
//            else{
//                Info("MSG: " + in.getContent());
//            }
    }
    
    private void esperarCancelBuscadores(){
        for (int i=0; i<buscadores.size(); i++){
            in = blockingReceive();
            if (! in.getContent().equals("turnOffCompleted")){
                i--;
            }
            //Info("MSG: "+in.getContent());
//            hayError = (in.getPerformative() != ACLMessage.INFORM);
//                if (hayError) {
//                    Info("\t" + "ERROR");
//                    estado = "EXIT";
//                    break;
//                }
//                else{
                  
//                }
        }
    }
    
    private ACLMessage esperarCancelComunicador(){
        return blockingReceive();
    }
    
    private void enviarCompraDrone(JsonValue resultado, String drone){   
        JsonObject msg = new JsonObject();
        msg.add("action", "comprar");
        msg.add("Tienda", resultado.asObject().get("tienda").asInt());
        msg.add("Referencia", resultado.asObject().get("reference"));
        msg.add("Serie", resultado.asObject().get("serie").asInt());
        msg.add("Precio", resultado.asObject().get("price").asInt());
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(drone, AID.ISLOCALNAME));
        send(out);
    }
    
    private void calcularParametrosSegunWorldSize(){
        calcularSensoresSegunWorldSize();
        calcularPosicionSegunWorldSize();
    }
    
    private void calcularSensoresSegunWorldSize(){
        int worldSize = mapa.getWidth() * mapa.getHeight();
        
        if (worldSize < 2000){
            sensoresBuscadores.add("thermal");
        }
        
        else {
            sensoresBuscadores.add("thermalHQ");
        }
    }
    
    private void calcularPosicionSegunWorldSize(){
        int worldSize = mapa.getWidth() * mapa.getHeight();
        int visionThermal = 0;
        
        if (worldSize < 2000){
            visionThermal = 3; // hemos comprado el thermal estandar   
        }
        
        else {
            visionThermal = 10; // hemos comprado el thermal HQ
        }
        
        ArrayList <Integer> posicion = new ArrayList<> ();
        posicion.add(visionThermal);
        posicion.add(visionThermal);
        matrizPosiciones.add(posicion);

        posicion = new ArrayList<> ();
        posicion.add(mapa.getWidth()-visionThermal);
        posicion.add(visionThermal);
        matrizPosiciones.add(posicion);

        posicion = new ArrayList<> ();
        posicion.add(mapa.getWidth()/2 );
        posicion.add(mapa.getHeight() - visionThermal);
        matrizPosiciones.add(posicion);
    }
    
    private void realizarLoginDrones(){
        for(int i = 0; i < buscadores.size(); i++){
            realizarLoginDrone(buscadores.get(i), matrizPosiciones.get(i).get(0), matrizPosiciones.get(i).get(1));
            
        }
        esperarLoginBuscadores();
        
        realizarLoginDrone(rescatador, mapa.getWidth()/2 , mapa.getHeight()/2);
        esperarLoginRescatador();
        
    }
    
    private void realizarLoginDrone(String nombre, int posx, int posy){
        JsonObject msg = new JsonObject();
        msg.add("action", "login");
        msg.add("posx", posx);
        msg.add("posy", posy);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(nombre, AID.ISLOCALNAME));
        send(out);
    }
    
    private void esperarLoginRescatador(){
        in = blockingReceive();
        Info("Despues de esperar Rescatador "+in.getContent());
    }
    
    private void esperarLoginBuscadores(){
        for(int i = 0; i < buscadores.size(); i++){
            in = blockingReceive();
            Info("Despues de esperar Buscador "+in.getContent());
        }
    }
    
    private void finalizarCompra(String nombre){
        JsonObject msg = new JsonObject();
        msg.add("action", "finalizarCompra");
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(nombre, AID.ISLOCALNAME));
        send(out);
    }
    
    private void gestionarMuerte(){
        String muerto = in.getSender().getName();
        boolean encontrado = false;
        
        for (int i=0; i<buscadores.size() && !encontrado; i++){
            if (muerto.contains(buscadores.get(i))){
                encontrado = true;
                buscadores.remove(i);
            }
        }
        
        if (buscadores.size() == 0){
            cancelarRescatador();
        }
       
    }
    
    private void cancelarRescatador(){
        JsonObject msg = new JsonObject();
        msg.add("action", "turnOff");
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(rescatador, AID.ISLOCALNAME));
        send(out);
        
        do {
            in = blockingReceive();
        } while (in.getSender().getName().equals(rescatador) && in.getContent().equals("turnOffCompleted"));
        
        estado = "CANCEL-COMUNICADOR";
    }
}