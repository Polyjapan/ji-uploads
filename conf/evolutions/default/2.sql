-- !Ups

alter table upload_requests
    add callback_url VARCHAR(250) default NULL null;

alter table upload_requests
    add callback_secret VARCHAR(250) default NULL null;

-- !Downs

alter table upload_requests
    drop column callback_url;

alter table upload_requests
    drop column callback_secret;
