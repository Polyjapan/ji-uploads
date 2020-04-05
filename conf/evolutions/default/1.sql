-- !Ups

create table containers
(
    container_id int auto_increment
        primary key,
    app_id int not null,
    container_name varchar(200) null,
    max_file_size_bytes int not null,
    constraint containers_app_id_container_name_uindex
        unique (app_id, container_name)
);

create table container_types
(
    container_id int not null,
    mime_type varchar(100) not null,
    extension varchar(10) not null,
    constraint containers_types_containers_container_id_fk
        foreign key (container_id) references containers (container_id)
            on update cascade on delete cascade
);

create index container_types_container_id_index
    on container_types (container_id);

create table uploads
(
    upload_id int auto_increment
        primary key,
    container_id int not null,
    uploader_type set('user', 'app') null,
    uploader_id int null,
    file_path varchar(200) not null,
    mime_type varchar(200) not null,
    size_bytes int not null,
    constraint uploads_containers_container_id_fk
        foreign key (container_id) references containers (container_id)
            on update cascade on delete cascade
);

create index uploads_container_id_index
    on uploads (container_id);

create index uploads_container_id_uploader_id_index
    on uploads (container_id, uploader_id);

create table upload_requests
(
    request_id int auto_increment
        primary key,
    request_ticket char(64) not null,
    container_id int not null,
    uploader_type set('user', 'app') null,
    uploader_id int null,
    replacement_policy set('no_replace', 'replace_one', 'replace_all') default 'no_replace' not null,
    replace_upload int null,
    upload_id int null,
    expires_at timestamp default (current_timestamp() + interval 12 hour) not null,
    constraint upload_requests_request_ticket_uindex
        unique (request_ticket),
    constraint upload_requests_containers_container_id_fk
        foreign key (container_id) references containers (container_id)
            on update cascade on delete cascade,
    constraint upload_requests_uploads_upload_id_fk
        foreign key (upload_id) references uploads (upload_id)
            on delete set null
);


-- !Downs