package com.ciandt.techgallery.service.impl;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

import com.googlecode.objectify.Key;

import com.ciandt.techgallery.Constants;
import com.ciandt.techgallery.persistence.dao.EmailNotificationDAO;
import com.ciandt.techgallery.persistence.dao.TechnologyCommentDAO;
import com.ciandt.techgallery.persistence.dao.TechnologyRecommendationDAO;
import com.ciandt.techgallery.persistence.dao.impl.EmailNotificationDAOImpl;
import com.ciandt.techgallery.persistence.dao.impl.TechGalleryUserDAOImpl;
import com.ciandt.techgallery.persistence.dao.impl.TechnologyCommentDAOImpl;
import com.ciandt.techgallery.persistence.dao.impl.TechnologyDAOImpl;
import com.ciandt.techgallery.persistence.dao.impl.TechnologyRecommendationDAOImpl;
import com.ciandt.techgallery.persistence.model.EmailNotification;
import com.ciandt.techgallery.persistence.model.TechGalleryUser;
import com.ciandt.techgallery.persistence.model.Technology;
import com.ciandt.techgallery.persistence.model.TechnologyComment;
import com.ciandt.techgallery.persistence.model.TechnologyRecommendation;
import com.ciandt.techgallery.service.EmailService;
import com.ciandt.techgallery.service.email.EmailConfig;
import com.ciant.techgallery.transaction.Transactional;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@Transactional
public class EmailServiceImpl implements EmailService {

  /*
   * Attributes --------------------------------------------
   */
  private static EmailServiceImpl instance;

  private static final Logger log = Logger.getLogger(EmailServiceImpl.class.getName());
  private static final String queueName = "email-queue";
  private static final String queueUrl = "/mail";

  // TODO: move inside create template.
  private static final String template = "template.example.email";
  private static final String templateEndorserment = "templateEndorserment.example.email";
  private static final String subject = "[Tech Gallery] Resumo do dia";
  private static final String reason = "Resumo do dia para os followers";

  /*
   * Constructors --------------------------------------------
   */
  private EmailServiceImpl() {}

  /**
   * Singleton method for the service.
   *
   * @author <a href="mailto:joaom@ciandt.com"> João Felipe de Medeiros Moreira </a>
   * @since 09/10/2015
   *
   * @return EmailServiceImpl instance.
   */
  public static EmailServiceImpl getInstance() {
    if (instance == null) {
      instance = new EmailServiceImpl();
    }
    return instance;
  }

  private InternetAddress from = null;
  private EmailNotificationDAO emailNotificationDao = EmailNotificationDAOImpl.getInstance();
  private TechnologyRecommendationDAO technologyRecommendationDao =
      TechnologyRecommendationDAOImpl.getInstance();
  private TechnologyCommentDAO technologyCommentDao = TechnologyCommentDAOImpl.getInstance();
  private TechGalleryUserDAOImpl techGalleryUserDao = TechGalleryUserDAOImpl.getInstance();
  private TechnologyDAOImpl technologyDao = TechnologyDAOImpl.getInstance();

  /**
   * Push email to queue.
   */
  @Override
  public void push(TechGalleryUser user, Technology technology, String recommendationsIds,
      String commentsIds) {
    QueueFactory.getQueue(queueName).add(
        TaskOptions.Builder.withUrl(queueUrl).param("userId", user.getId().toString())
            .param("technologyId", technology.getId())
            .param("recommendationsIds", recommendationsIds).param("commentsIds", commentsIds));
  }

  @Override
  public void push(TechGalleryUser endorserUser, TechGalleryUser endorsedUser,
      Technology technology) {
    QueueFactory.getQueue(queueName).add(
        TaskOptions.Builder.withUrl(queueUrl).param("userId", endorserUser.getId().toString())
            .param("endorsedUser", endorsedUser.getId().toString())
            .param("technologyId", technology.getId()));
  }

  /**
   * Execute email from queue.
   */
  @Override
  public void execute(String userId, String technologyId, String recommendationsIds,
      String commentsIds, String serverUrl) {
    if (!commentsIds.isEmpty()) {
      String[] commentIds = commentsIds.split(",");
      List<TechnologyComment> comments = new ArrayList<TechnologyComment>();
      for (String id : commentIds) {
        if (!id.isEmpty()) {
          comments.add(technologyCommentDao.findById(Long.parseLong(id)));
        }
      }
    }
    if (!recommendationsIds.isEmpty()) {
      String[] recommendIds = recommendationsIds.split(",");
      List<TechnologyRecommendation> recommendations = new ArrayList<TechnologyRecommendation>();
      for (String id : recommendIds) {
        if (!id.isEmpty()) {
          recommendations.add(technologyRecommendationDao.findById(Long.parseLong(id)));
        }
      }
    }
    TechGalleryUser user = techGalleryUserDao.findById(Long.parseLong(userId));
    Technology technology = technologyDao.findById(technologyId);

    // TODO extract method to build template (mustache).
    Map<String, String> variableValue = new HashMap<String, String>();
    variableValue.put("${receiverName}", user.getName());
    variableValue.put("${user}", user.getName());
    variableValue.put("${technology}", technology.getName());
    EmailConfig email = new EmailConfig(subject, "emailtemplates" + File.separator + template,
        variableValue, null, reason, user.getEmail());
    sendEmail(email);
  }
  
  @Override
  public void execute(String userId, String endorsedUser, String technologyId) {
    TechGalleryUser endorser = techGalleryUserDao.findById(Long.parseLong(userId));
    TechGalleryUser endorsed = techGalleryUserDao.findById(Long.parseLong(endorsedUser));
    Technology technology = technologyDao.findById(technologyId);
    
    // TODO extract method to build template (mustache).
    Map<String, String> variableValue = new HashMap<String, String>();
    variableValue.put("${endorser}", endorser.getName());
    variableValue.put("${endorsed}", endorsed.getName());
    variableValue.put("${technology}", technology.getName());
    EmailConfig email =
        new EmailConfig(subject, "emailtemplates" + File.separator + templateEndorserment,
            variableValue, null, reason, endorsed.getEmail());
    sendEmail(email);
  }

  private void sendEmail(EmailConfig email) {
    try {
      Message msg = prepareMessage(email);
      Transport.send(msg);
      registerEmailNotification(email, true);

    } catch (Throwable err) {
      Long notificationId = registerEmailNotification(email, false);
      log.log(Level.SEVERE, "Error when attempting to send email " + notificationId, err);
    }
  }

  private Message prepareMessage(EmailConfig email)
      throws UnsupportedEncodingException, MessagingException {

    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);
    Message msg = new MimeMessage(session);
    msg.setFrom(getFrom());
    for (String to : email.getTo()) {
      msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
    }
    msg.setSubject(email.getSubject());
    email.processTemplate();
    msg.setText(email.getBody());
    return msg;
  }

  private long registerEmailNotification(EmailConfig email, boolean success) {
    EmailNotification emailNotification = new EmailNotification();
    emailNotification.setRecipients(Arrays.asList(email.getTo()));
    emailNotification.setRule(email.getRule());
    emailNotification.setReason(email.getReason());
    emailNotification.setTimestampSend(System.currentTimeMillis());
    emailNotification.setEmailStatus(success ? "SUCCESS" : "FAILURE");

    Key<EmailNotification> key = emailNotificationDao.add(emailNotification);
    return key.getId();
  }

  private InternetAddress getFrom() throws UnsupportedEncodingException {
    if (from == null) {
      log.info("app email from address set to: " + Constants.APP_EMAIL);
      from = new InternetAddress(Constants.APP_EMAIL, "no-reply@techgallery.com");
    }
    return from;
  }
}
