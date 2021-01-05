/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Francisco José Molina Sánchez, Pedro Serrano Pérez,
 *         Miguel Ángel Molina Jordán
 */
public class Seeker extends DroneDelMundo{
    final static String TIPO = "SEEKER";
    
    private boolean primeraLecturaThermal;
    private int siguienteCasilla;
    private ArrayList<ArrayList<Integer>> objetivosEncontrados;
    ArrayList <ArrayList<Double>> thermal;
    
    
    
    // atributos heredados del AgenteDrone:
    // protected YellowPages yp;
    // protected String estado, servicio, mundo;
    // protected String worldManager, convID;
    // protected boolean hayError;
    // protected ACLMessage in, out;

    @Override
    public void setup() {
        super.setup();
        tipo = "SEEKER"; 
        primeraLecturaThermal = true;
        objetivosEncontrados = new ArrayList();
        anguloActualDrone = 90;
        siguienteCasilla = -1;
        zActual = 0; //mapa.getLevel(xActualDrone, yActualDrone);
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
                break;
            case "COMPRAR-SENSORES":
                comprar();
                break;
            case "ESPERAR-ORDEN":
                Info("Esperando orden");
                in = blockingReceive();
                Info(in.getContent());
                String accion = obtenerResultado();
                hayError = in.getPerformative() != ACLMessage.INFORM;
                if (hayError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " Error en mensaje");
                    estado = "CANCEL-WM";
                    break;
                }
                if (accion.equals("turnOff")){
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                else if(accion.equals("login")){
                    estado = "REALIZAR-LOGIN";
                    break;
                }
                else if(accion.equals("recargar")){
                    bajarAlSuelo();
                    if (!iniciarRecarga()){ //No hay error
                        Info("He recargado");
                        energia = 1000;
                        estado = "TRAZAR-RECORRIDO";
                    }
                    else{
                        Info("No he recargado");
                    }
                    break;
                }
                else if(accion.equals("noRecargar")){
                    estado = "INFORMAR-MUERTE";
                    break;
                }else{
                    estado = "CHECKOUT-LARVA";
                    break;
                }
            case "FINALIZAR-COMPRA":
                enviarCoins();
                estado = "ESPERAR-ORDEN";
                break;
            case "REALIZAR-LOGIN":
                cargarMapa();
                boolean error = realizarLoginWM();
                informarCoachLoginRealizado();
                if (!error){
                    estado = "LEER-SENSORES";
                }
                break;
            case "LEER-SENSORES":
                actualizarMapaSensores();
                actualizarValorSensores();
                if (comprobarAlive()){ // nos llevará a INFORMAR MUERTE si hemos muerto
                    estado = "TRAZAR-RECORRIDO";
                } 
                break;
            case "TRAZAR-RECORRIDO":
                if (comprobarEnergia()){
                    solicitarRecargaACoach();
                    estado = "ESPERAR-ORDEN";
                }
                else{
                    buscarObjetivo();
                    
                }
                break;
            case "INFORMAR-MUERTE":
                Info ("El drone ha muerto");
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
    protected void actualizarValorSensores (){
        super.actualizarValorSensores();
        
        // crear matriz para thermal
        thermal = new ArrayList <> ();
        for (int i=0; i< mapaSensores.get("thermal").asArray().size(); i++){
            thermal.add (new ArrayList <Double> ());
            for (int j=0;j<mapaSensores.get("thermal").asArray().get(i).asArray().size();j++){
                thermal.get(i).add(mapaSensores.get("thermal").asArray().get(i).asArray().get(j).asDouble());
            }
        }
    }
    
    private boolean comprobarEnergia(){
        return energia < umbralEnergia;
    }
        
    private void buscarObjetivo(){
        
        for (int x=0; x < thermal.size(); x++) {
        System.out.print("|");
            for (int y=0; y < thermal.get(x).size(); y++) {
                System.out.print (thermal.get(x).get(y));
                if (y!=thermal.get(x).size()-1) System.out.print("\t");
            }
        System.out.println("|");
      }
        
        double minThermal = Double.MAX_VALUE;
        int siguientePosX = 0;
        int siguientePosY = 0;
        
        if(primeraLecturaThermal){
            for (int i = 0; i < thermal.size(); i++) {
                for (int j = 0; j < thermal.size(); j++) {
                    
                    if (i == 0 && j == 0 || i == 0 && j == (thermal.size()-1)/2 || i == 0 && j == thermal.size()-1
                        || i == (thermal.size()-1)/2 && j == 0 || i == (thermal.size()-1)/2 && j == thermal.size()-1
                        || i == thermal.size()-1 && j == 0 || i == thermal.size()-1 && j == (thermal.size()-1)/2 
                        || i == thermal.size()-1 && j == thermal.size()-1 ){
                        
                        if (thermal.get(i).get(j) < minThermal){
                            minThermal = thermal.get(i).get(j);
                            siguientePosX = i;
                            siguientePosY = j;
                            Info ("Minimo thermal: " + minThermal);
                            Info ("siguiente x: " + i);
                            Info ("siguiente y: " + j);
                        }         
                    }
                    
                    if(thermal.get(i).get(j) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(xActualDrone + j - 3);
                        arr.add(yActualDrone + i - 3);
                        objetivosEncontrados.add(arr);
                    }
                }
            }
            if(objetivosEncontrados.size()>0){
                Info("Objetivo encontrado");
                notificarCoachObjetivosEncontrados();
                estado = "ESPERAR-ORDEN";
            }
            primeraLecturaThermal = false;
        }
        else {
            for (int i = 0; i < thermal.size(); i++) {
                for (int j = 0; j < thermal.size(); j++) {
                    
                    if (!(i > 0 && j > 0 && i<thermal.size()-1 && j<thermal.size()-1)){
                        if (i == 0 && j == 0 || i == 0 && j == (thermal.size()-1)/2 || i == 0 && j == thermal.size()-1
                        || i == (thermal.size()-1)/2 && j == 0 || i == (thermal.size()-1)/2 && j == thermal.size()-1
                        || i == thermal.size()-1 && j == 0 || i == thermal.size()-1 && j == (thermal.size()-1)/2 
                        || i == thermal.size()-1 && j == thermal.size()-1 ){
                        
                            if (thermal.get(i).get(j) < minThermal){
                                minThermal = thermal.get(i).get(j);
                                siguientePosX = i;
                                siguientePosY = j;
                                Info ("Minimo thermal: " + minThermal);
                                Info ("siguiente x: " + i);
                                Info ("siguiente y: " + j);
                            }         
                        }

                        if(thermal.get(i).get(j) == 0){
                            ArrayList<Integer> arr = new ArrayList();
                            arr.add(xActualDrone + j - 3);
                            arr.add(yActualDrone + i - 3);
                            objetivosEncontrados.add(arr);
                        } 
                    }
                    
                }
            }
            if(objetivosEncontrados.isEmpty()){
                if (siguientePosX == 0 && siguientePosY == 0){
                    xActualDrone --;
                    yActualDrone --;
                    nuevoAngulo = -45;
                }
                else if (siguientePosX == 0 && siguientePosY == (thermal.size()-1)/2){
                    yActualDrone --;
                    nuevoAngulo = 0;
                }
                else if (siguientePosX == 0 && siguientePosY == thermal.size()-1){
                    xActualDrone ++;
                    yActualDrone --;
                    nuevoAngulo = 45;
                }
                else if (siguientePosX == (thermal.size()-1)/2 && siguientePosY == thermal.size()-1){
                    xActualDrone ++;
                    nuevoAngulo = 90;
                }
                else if (siguientePosX == thermal.size()-1 && siguientePosY == thermal.size()-1){
                    xActualDrone ++;
                    yActualDrone ++;
                    nuevoAngulo = 135;
                }
                else if (siguientePosX == thermal.size()-1 && siguientePosY == (thermal.size()-1)/2){
                    yActualDrone ++;
                    nuevoAngulo = 180;
                }
                else if (siguientePosX == thermal.size()-1 && siguientePosY == 0){
                    xActualDrone --;
                    yActualDrone ++;
                    nuevoAngulo = -135;
                }
                else if (siguientePosX == (thermal.size()-1)/2 && siguientePosY == 0){
                    xActualDrone --;
                    nuevoAngulo = -90;
                }
                moverse();
                
            }
            else{
                Info("Objetivo enconntrado");
                notificarCoachObjetivosEncontrados();
                estado = "ESPERAR-ORDEN";
            }
        }
    }
    
    private void notificarCoachObjetivosEncontrados(){
        JsonObject msg = new JsonObject();
        JsonArray jarr = new JsonArray();
        for (int i = 0; i <objetivosEncontrados.size() ; i++) {
            JsonArray val = new JsonArray();
            val.add(objetivosEncontrados.get(i).get(0));
            val.add(objetivosEncontrados.get(i).get(1));
            jarr.add(val);
        }
        msg.add("rescatarObjetivos", jarr);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        send(out);
    }
    

}