alter table jsimplelist.profiles
    add column avatar_icon text not null default 'person',
    add column avatar_colour text not null default 'blue';

alter table jsimplelist.profiles
    add constraint profiles_avatar_icon_check
    check (
        avatar_icon in (
            'person',
            'flower',
            'cat',
            'horse',
            'lightning',
            'coffee',
            'helmet',
            'paw',
            'book',
            'alien',
            'f1car',
            'music',
            'home',
            'heart',
            'star',
            'starfish'
        )
    );

alter table jsimplelist.profiles
    add constraint profiles_avatar_colour_check
    check (
        avatar_colour in (
            'blue',
            'purple',
            'pink',
            'orange',
            'green',
            'teal',
            'red',
            'brown'
        )
    );
