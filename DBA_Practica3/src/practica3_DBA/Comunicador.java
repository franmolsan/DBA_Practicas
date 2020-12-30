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
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;

/**
 *
 * @author Francisco José Molina Sánchez, Pedro Serrano Pérez,
 *         Miguel Ángel Molina Jordán
 */
public class Comunicador extends AgenteDrone {
    // atributos heredados del AgenteDrone:
    
    // protected YellowPages yp;
    // protected String estado, servicio, mundo;
    // protected String worldManager, convID;
    // protected boolean hayError;
    // protected ACLMessage in, out;
    // protected Map2DGrayscale mapa;
    private ArrayList<String> buscadores = new ArrayList<String>();
    private String rescatador = "";
    
    @Override
    public void setup() {
        super.setup();
        buscadores.add("NobitaSinGafas2");
        //buscadores.add("OvejaOscar");
        //buscadores.add("DoraLaExploradora");
    }
    
    @Override
    public void plainExecute() {
        plainWithErrors();
    }


    public void plainWithErrors() {
        // Basic iteration
        switch (estado.toUpperCase()) {
            case "CHECKIN-LARVA":
                Info("Checkin in LARVA with " + _identitymanager);
                in = suscribirseA(_identitymanager); // As seen in slides
                hayError = (in.getPerformative() != ACLMessage.INFORM);
                if (hayError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Checkin failed due to " + getDetailsLARVA(in));
                    estado = "EXIT";
                    break;
                }
                estado = "SUBSCRIBE-WM";
                Info("\tCheckin ok");
                break;
            case "SUBSCRIBE-WM":
                Info("Retrieve who is my WM");
                // First update Yellow Pages
                in = obtenerYP(_identitymanager); // As seen oon slides
                Info("YP obtenidas");
                hayError = in.getPerformative() != ACLMessage.INFORM;
                if (hayError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Query YellowPages failed due to " + getDetailsLARVA(in));
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                yp = new YellowPages();
                yp.updateYellowPages(in);
                // It might be the case that YP are right but we dont find an appropriate service for us, then leave
                if (yp.queryProvidersofService(servicio).isEmpty()) {
                    Info("\t" + "There is no agent providing the service " + servicio);
                    estado = "CHECKOUT-LARVA";
                    break;
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
                    break;
                }
                // Keep the Conversation ID and spread it amongs the team members
                convID = in.getConversationId();
                Info(convID);
                // Move on to get the map
                estado = "PROCESS-MAP";
                break;

            case "PROCESS-MAP":
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
                    } else {
                        Info("\t" + "There was an error processing and saving the image ");
                        estado = "CANCEL-WM";
                        break;
                    }
                } else {
                    Info("\t" + "There is no map found in the message");
                    estado = "CANCEL-WM";
                    break;
                }
                estado = "DESPERTAR-DRONES";
                break;
            case "DESPERTAR-DRONES":
                //despertarAWACS();
//                try {
//                    //Thread.sleep(5000);
//                }
//                catch (Exception ex){
//                    Info("Error en AWACS: " + ex);
//                };
                Info("ENTRO");
                despertarDrones();
                estado = "ESPERAR-SETUP-DRONES";
                break;
            case "ESPERAR-SETUP-DRONES":
                Info("Esperando el setup de los buscadores");
                esperarSetupBuscadores();
                Info("Setup de los buscadores completo");
                estado = "CANCEL-BUSCADORES";
            case "CANCEL-BUSCADORES":
                informarTodosObjetivosRescatadosABuscadores();
                Info("Esperando cancel Buscadores");
                esperarCancelBuscadores();
                Info("Buscadores Cancelados");
                estado = "CANCEL-WM";
                break;
            case "CANCEL-WM":
                Info("Closing the game");
                in = enviarCancelA(worldManager);
                
                // apagar AWACS para que acabe bien el programa
                in = enviarCancelA("AWACS");
                
                estado = "CHECKOUT-LARVA";
                break;
            case "CHECKOUT-LARVA":
                Info("Exit LARVA");
                in = enviarCancelA(_identitymanager);
                estado = "EXIT";
                break;
            case "EXIT":
                Info("The agent dies");
                _exitRequested = true;
                break;
        }
    }
    
    private void despertarDrones(){   
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(new JsonObject().add("idListener", getAID().toString()).toString());
        out.setProtocol("REGULAR");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.QUERY_IF);
        for (int i=0; i<buscadores.size(); i++){
            out.addReceiver(new AID(buscadores.get(i), AID.ISLOCALNAME));
        }
        send(out);
    }
    
    private void despertarAWACS(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(new JsonObject().toString());
        out.setProtocol("REGULAR");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.QUERY_IF);
        out.addReceiver(new AID("AWACS", AID.ISLOCALNAME));
        
        send(out);
    }
    
    
    private void informarTodosObjetivosRescatadosABuscadores(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent("turnOff");
        out.setProtocol("REGULAR");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.INFORM);
        for (int i=0; i<buscadores.size(); i++){
            out.addReceiver(new AID(buscadores.get(i), AID.ISLOCALNAME));
        }
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
    
    private void esperarCancelBuscadores(){
        for (int i=0; i<buscadores.size(); i++){
            in = blockingReceive();
            hayError = (in.getPerformative() != ACLMessage.INFORM);
                if (hayError) {
                    Info("\t" + "ERROR");
                    estado = "EXIT";
                    break;
                }
                else{
                    Info("MSG: "+in.getContent());
                }
        }
    }
}