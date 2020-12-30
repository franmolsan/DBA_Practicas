/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */

package practica3_DBA;

import AppBoot.ConsoleBoot;

public class main {
    static ConsoleBoot _app;
    
    /**
    * @author: Jose Armando Albarado Mamani, Francisco José Molina Sánchez
    * @params: args
     */
    public static void main(String[] args) {
        _app = new ConsoleBoot("PRACTICA3_DBA", args);
        _app.selectConnection();
        
        // primero lanzamos AWACS para visualizar todo
        //_app.launchAgent("AWACS", Awacs.class);
        
        // luego lanzamos el resto de agentes
        _app.launchAgent("NobitaSinGafas2", Seeker.class);
        _app.launchAgent("EduardoManosTijeras2", Rescuer.class);
        _app.launchAgent("Cerebro Computadora2", Coach.class);
        //_app.launchAgent("OvejaOscar", Seeker.class);
        //_app.launchAgent("CerditaPeggy", Seeker.class);
        
        _app.shutDown();
    }
    
}
