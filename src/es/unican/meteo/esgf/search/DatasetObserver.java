package es.unican.meteo.esgf.search;

/**
 * Observer of harvesting interface. Observes if download has been progressed,
 * has been completed or an error has happened.
 * 
 * Must be implemented in the class where they will carry out the necessary
 * changes consistent with the observed in the download.
 * 
 * Observer is a software design pattern in which an object, called the subject,
 * maintains a list of its dependents, called observers, and notifies them
 * automatically of any state changes, usually by calling one of their methods.
 * It is mainly used to implement distributed event handling systems
 * 
 * @author Karem Terry
 * 
 */
public interface DatasetObserver {

    /**
     * If change on harvest state is notified
     * 
     * @param dataset
     *            a {@link Dataset}
     */
    public void onChangeOfHarvestState(Dataset dataset);
}
