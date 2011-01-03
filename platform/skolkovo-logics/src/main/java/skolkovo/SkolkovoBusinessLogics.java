package skolkovo;

import net.sf.jasperreports.engine.JRException;
import platform.interop.Compare;
import platform.server.auth.User;
import platform.server.classes.*;
import platform.server.data.sql.DataAdapter;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.entity.filter.CompareFilterEntity;
import platform.server.form.entity.filter.NotNullFilterEntity;
import platform.server.form.entity.filter.RegularFilterEntity;
import platform.server.form.entity.filter.RegularFilterGroupEntity;
import platform.server.form.navigator.NavigatorElement;
import platform.server.form.view.DefaultFormView;
import platform.server.form.view.FormView;
import platform.server.logics.BusinessLogics;
import platform.server.logics.linear.LP;
import skolkovo.api.remote.SkolkovoRemoteInterface;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.SQLException;

public class SkolkovoBusinessLogics extends BusinessLogics<SkolkovoBusinessLogics> implements SkolkovoRemoteInterface {

    public SkolkovoBusinessLogics(DataAdapter adapter, int exportPort) throws IOException, ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException, FileNotFoundException, JRException {
        super(adapter, exportPort);
    }

    AbstractCustomClass participant;

    ConcreteCustomClass project;
    ConcreteCustomClass expert;
    ConcreteCustomClass cluster;
    ConcreteCustomClass claimer;
    ConcreteCustomClass document;

    ConcreteCustomClass vote;

    protected void initGroups() {
    }

    protected void initClasses() {

        participant = addAbstractClass("participant", "Участник", baseClass.named);

        project = addConcreteClass("project", "Проект", baseClass.named);
        expert = addConcreteClass("expert", "Эксперт", participant);
        cluster = addConcreteClass("cluster", "Кластер", baseClass.named);
        claimer = addConcreteClass("claimer", "Заявитель", participant);
        document = addConcreteClass("document", "Документ", baseClass.named);

        vote = addConcreteClass("vote", "Заседание", baseClass);
    }

    LP voteProject;
    LP projectDate;
    LP expertCluster;
    LP projectCluster;
    LP projectClaimer;
    LP participantEmail;

    LP documentProject;
    LP documentFile;

    LP expertInVote;
    LP voteStartDate;
    LP voteEndDate;

    LP voteOpened;
    LP projectVote;

    LP expertVoteDone;
    LP expertVoteConnected;
    LP expertVoteInCluster;
    LP expertVoteInnovative;
    LP expertVoteInnovativeComment;
    LP expertVoteForeign;
    LP expertVoteCompetent;
    LP expertVoteComplete;
    LP expertVoteCompleteComment;

    LP projectExpertVote;
    LP voteDoneExperts;
    LP voteSuccessed;
    LP projectValuedVote;
    LP projectNeedExtraVote;

    protected void initProperties() {

        LP addDays = addSFProp("prm1+prm2", DateClass.instance, 2);
        LP requiredPeriod = addDProp(baseGroup, "votePeriod", "Срок заседания", IntegerClass.instance);
        LP requiredQuantity = addDProp(baseGroup, "voteRequiredQuantity", "Кол-во экспертов", IntegerClass.instance);
        LP limitExperts = addDProp(baseGroup, "limitExperts", "Кол-во прогол. экспертов", IntegerClass.instance);

        voteProject = addDProp("voteProject", "Проект (ИД)", project, vote); addJProp(baseGroup, "Проект", name, voteProject, 1);
        projectDate = addDProp(baseGroup, "projectDate", "Дата проекта", DateClass.instance, project);
        expertCluster = addDProp("expertCluster", "Кластер (ИД)", cluster, expert); addJProp(baseGroup, "Кластер", name, expertCluster, 1);

        projectCluster = addDProp("projectCluster", "Кластер (ИД)", cluster, project); addJProp(baseGroup, "Кластер", name, projectCluster, 1);
        LP voteCluster = addJProp("Кластер (ИД)", projectCluster, voteProject, 1); addJProp(baseGroup, "Кластер", name, voteCluster, 1);

        projectClaimer = addDProp("projectClaimer", "Заявитель (ИД)", claimer, project); addJProp(baseGroup, "Заявитель", name, projectClaimer, 1);
        participantEmail = addDProp(baseGroup, "participantEmail", "E-mail", StringClass.get(50), participant);

        documentProject = addDProp("documentProject", "Проект (ИД)", project, document); addJProp(baseGroup, "Проект", name, documentProject, 1);
        documentFile = addDProp("documentFile", "Файл", WordClass.instance, document);

        expertInVote = addDProp(baseGroup, "expertInVote", "Вкл", LogicalClass.instance, expert, vote); // !!! нужно отослать письмо с документами и т.д

        voteStartDate = addDProp(baseGroup, "expertVoteStartDate", "Дата начала", DateClass.instance, vote);
        voteEndDate = addJProp(baseGroup, "expertVoteEndDate", "Дата окончания", addDays, voteStartDate, 1, requiredPeriod);

        voteOpened = addJProp(baseGroup, "expertVoteOpened", "Открыто", greater2, voteEndDate, 1, currentDate);
        projectVote = addCGProp(baseGroup, false, "projectVote", "Тек. заседание", addJProp(and1, 1, voteOpened, 1), voteOpened, voteProject, 1); // активно только одно заседание

        projectExpertVote = addCGProp(baseGroup, false, "projectExpertVote", "Заседание", addJProp(and1, 1, expertInVote, 2, 1), expertInVote, voteProject, 1, 2); // только один раз может голосовать

        // результаты голосования
        expertVoteDone = addDProp(baseGroup, "expertVoteDone", "Выполнено", LogicalClass.instance, expert, vote);
        expertVoteConnected = addDProp(baseGroup, "expertVoteConnected", "Аффилирован", LogicalClass.instance, expert, vote);
        expertVoteInCluster = addDProp(baseGroup, "expertVoteInCluster", "Соот-ет кластеру", LogicalClass.instance, expert, vote);
        expertVoteInnovative = addDProp(baseGroup, "expertVoteInnovative", "Подходит", LogicalClass.instance, expert, vote);
        expertVoteInnovativeComment = addDProp(baseGroup, "expertVoteInnovativeComment", "Подходит (комм.)", TextClass.instance, expert, vote);
        expertVoteForeign = addDProp(baseGroup, "expertVoteForeign", "Инн. спец.", LogicalClass.instance, expert, vote);
        expertVoteCompetent = addDProp(baseGroup, "expertVoteCompetent", "Компет.", IntegerClass.instance, expert, vote);
        expertVoteComplete = addDProp(baseGroup, "expertVoteComplete", "Полная инф.", IntegerClass.instance, expert, vote);
        expertVoteCompleteComment = addDProp(baseGroup, "expertVoteCompleteComment", "Полная инф. (комм.)", TextClass.instance, expert, vote);

        voteDoneExperts = addSGProp(baseGroup, "Проголосовало", addJProp(and1, addCProp(IntegerClass.instance, 1), expertVoteDone, 1, 2), 2); // сколько экспертов высказалось
        voteSuccessed = addJProp(baseGroup, "Состоялся", groeq2, voteDoneExperts, 1, limitExperts); // достаточно экспертов

        LP projectIsSuccessedVote = addCGProp(baseGroup, false, "projectIsSuccessedVote", "Достаточно оценок", addJProp(and1, 1, voteSuccessed, 1), voteSuccessed, voteProject, 1); // если есть состоявшееся заседание
        LP projectClosedVotes = addJProp(andNot1, is(project), 1, projectVote, 1); // нету заседаний
        projectValuedVote = addJProp(baseGroup, "projectValuedVote", "Оценен", and1, projectIsSuccessedVote, 1, projectClosedVotes, 1); // нет открытого заседания и есть состояшееся заседания
        projectNeedExtraVote = addJProp(and(true, true), is(project), 1, projectVote, 1, projectIsSuccessedVote, 1); // есть открытое заседания и есть состояшееся заседания !!! нужно создать новое заседание

        addConstraint(addJProp("Эксперт не соответствует необходимому кластеру", diff2,
                        expertCluster, 1, addJProp(and1, voteCluster, 2, expertInVote, 1, 2), 1, 2), false);

        addConstraint(addJProp("Количество экспертов не соответствует требуемому", andNot1, is(vote), 1, addJProp(equals2, requiredQuantity,
                addSGProp(addJProp(and1, addCProp(IntegerClass.instance, 1), expertInVote, 2, 1), 2), 1), 1), false);
    }

    protected void initTables() {
    }

    protected void initIndexes() {
    }

    protected void initNavigators() throws JRException, FileNotFoundException {
        addFormEntity(new ProjectFormEntity(baseElement, 10));
        addFormEntity(new ExpertFormEntity(baseElement, 15));
        addFormEntity(new GlobalFormEntity(baseElement, 20));
    }

    protected void initAuthentication() throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        User admin = addUser("admin", "fusion");
        admin.addSecurityPolicy(permitAllPolicy);
    }

    private class ProjectFormEntity extends FormEntity<SkolkovoBusinessLogics> {
        private ObjectEntity objProject;
        private ObjectEntity objVote;
        private ObjectEntity objDocument;
        private ObjectEntity objExpert;

        private ProjectFormEntity(NavigatorElement parent, int iID) {
            super(parent, iID, "Реестр проектов");

            objProject = addSingleGroupObject(project, baseGroup);
            addObjectActions(this, objProject);

            objVote = addSingleGroupObject(vote, baseGroup);
            addObjectActions(this, objVote);

            objDocument = addSingleGroupObject(document, baseGroup);
            addObjectActions(this, objDocument);

            objExpert = addSingleGroupObject(expert, baseGroup);

            addPropertyDraw(objVote, objExpert, baseGroup);

            addFixedFilter(new CompareFilterEntity(addPropertyObject(voteProject, objVote), Compare.EQUALS, objProject));
            addFixedFilter(new CompareFilterEntity(addPropertyObject(documentProject, objDocument), Compare.EQUALS, objProject));
            addFixedFilter(new CompareFilterEntity(addPropertyObject(expertCluster, objExpert), Compare.EQUALS, addPropertyObject(projectCluster, objProject)));

            RegularFilterGroupEntity expertFilterGroup = new RegularFilterGroupEntity(genID());
            expertFilterGroup.addFilter(new RegularFilterEntity(genID(),
                                  new NotNullFilterEntity(addPropertyObject(expertInVote, objExpert, objVote)),
                                  "В заседании",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0)), true);
            addRegularFilterGroup(expertFilterGroup);

            RegularFilterGroupEntity projectFilterGroup = new RegularFilterGroupEntity(genID());
            projectFilterGroup.addFilter(new RegularFilterEntity(genID(),
                                  new NotNullFilterEntity(addPropertyObject(projectValuedVote, objProject)),
                                  "Оценен",
                                  KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0)));
            addRegularFilterGroup(projectFilterGroup);
        }

        @Override
        public FormView createDefaultRichDesign() {
            DefaultFormView design = (DefaultFormView)super.createDefaultRichDesign();

            return design;
        }
    }

    private class GlobalFormEntity extends FormEntity<SkolkovoBusinessLogics> {

        private GlobalFormEntity(NavigatorElement parent, int iID) {
            super(parent, iID, "Глобальные параметры");

            addPropertyDraw(baseGroup, true);
        }
    }

    private class ExpertFormEntity extends FormEntity<SkolkovoBusinessLogics> {
        private ObjectEntity objExpert;
        private ObjectEntity objVote;

        private ExpertFormEntity(NavigatorElement parent, int iID) {
            super(parent, iID, "Статистика по экспертам");

            objExpert = addSingleGroupObject(expert, baseGroup);
            addObjectActions(this, objExpert);

            objVote = addSingleGroupObject(vote, baseGroup);

            addPropertyDraw(objVote, objExpert, baseGroup);

            addFixedFilter(new NotNullFilterEntity(addPropertyObject(expertInVote, objExpert, objVote)));
        }
    }

    public VoteInfo getVoteInfo(String login, int voteId) throws RemoteException {
        //todo:
        VoteInfo voteInfo = new VoteInfo();
        voteInfo.expertName = "Some name";
        return voteInfo;
    }

    public void setVoteInfo(int expertId, int voteId, VoteInfo voteInfo) throws RemoteException {
        //todo:
    }
}
