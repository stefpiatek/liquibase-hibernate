package liquibase.ext.hibernate.diff;


import liquibase.change.Change;
import liquibase.change.core.AddForeignKeyConstraintChange;
import liquibase.database.Database;
import liquibase.diff.Difference;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.ForeignKey;
import liquibase.structure.core.ForeignKeyConstraintType;


/**
 * Hibernate doesn't know about all the variations that occur with foreign keys but just whether the FK exists or not.
 * To prevent changing customized foreign keys, all foreign key changes from hibernate are suppressed, except for on delete.
 */
public class OnDeleteEnabledChangedForeignKeyChangeGenerator extends liquibase.diff.output.changelog.core.ChangedForeignKeyChangeGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (ForeignKey.class.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Change[] fixChanged(DatabaseObject changedObject, ObjectDifferences differences, DiffOutputControl control,
                               Database referenceDatabase, Database comparisonDatabase, ChangeGeneratorChain chain) {
        if (referenceDatabase instanceof HibernateDatabase || comparisonDatabase instanceof HibernateDatabase) {


            Change[] changes = super.fixChanged(changedObject, differences, control, referenceDatabase, comparisonDatabase, chain);

            if (changes != null && changes.length == 2) {
                AddForeignKeyConstraintChange addFkChange = (AddForeignKeyConstraintChange) changes[1];
                if (differences.isDifferent("deleteRule")) {
                    Difference deleteRule = differences.getDifference("deleteRule");
                    ForeignKeyConstraintType hibernateDeleteAction = (ForeignKeyConstraintType) deleteRule.getReferenceValue();
                    ForeignKeyConstraintType actualDeleteAction = (ForeignKeyConstraintType) deleteRule.getComparedValue();
                    if (hibernateDeleteAction == null || hibernateDeleteAction == ForeignKeyConstraintType.importedKeyNoAction) {
                        if (actualDeleteAction != null && actualDeleteAction != ForeignKeyConstraintType.importedKeyNoAction) {
                            // changed from onDelete to doNothing, most likely added ondelete by hand
                            differences.removeDifference("deleteRule");
                        } else {
                            // no real change -> remove it
                            differences.removeDifference("deleteRule");
                        }
                    } else {
                        addFkChange.setOnDelete(hibernateDeleteAction);
                    }
                }
                if (differences.isDifferent("updateRule")) {
                    Difference updateRule = differences.getDifference("updateRule");
                    ForeignKeyConstraintType hibernateUpdateAction = (ForeignKeyConstraintType) updateRule.getReferenceValue();
                    ForeignKeyConstraintType actualUpdateAction = (ForeignKeyConstraintType) updateRule.getComparedValue();
                    if (hibernateUpdateAction == null || hibernateUpdateAction == ForeignKeyConstraintType.importedKeyNoAction) {
                        if (actualUpdateAction != null && actualUpdateAction != ForeignKeyConstraintType.importedKeyNoAction) {
                            // changed from onDelete to doNothing, most likely added onupdate by hand
                            differences.removeDifference("updateRule");
                        } else {
                            // no real change -> remove it
                            differences.removeDifference("updateRule");
                        }
                    } else {
                        addFkChange.setOnUpdate(hibernateUpdateAction);
                    }
                }
            }

            if (!differences.hasDifferences()) {
                return null;
            }

            return changes;
        }

        return super.fixChanged(changedObject, differences, control, referenceDatabase, comparisonDatabase, chain);
    }
}
