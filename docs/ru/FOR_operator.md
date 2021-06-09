---
title: 'Оператор FOR'
---

Оператор `FOR` - создание [действия](Actions.md), реализующего [цикл](Loop_FOR.md).

### Синтаксис

    FOR expression [ORDER [DESC] orderExpr1, ..., orderExprN]
    [NEW [alias =] className]
    DO action
    [ELSE alternativeAction]

Возможен вариант, когда в операторе присутствует блок `NEW`, а условие не указывается (считается равным `TRUE`), в этом случае синтаксис выглядит следующим образом:

    NEW [alias =] className
    action

### Описание

Оператор `FOR` создает действие, реализующее цикл. Этот оператор должен добавлять свои локальные параметры при задании условия. Эти параметры соответствуют перебираемым объектам и не являются параметрами создаваемого действия. Также в блоке `NEW `можно указать имя [класса](Classes.md), объект которого будет создаваться для каждого набора объектов, удовлетворяющего условию. Этому объекту задается имя, которое будет использоваться в качестве имени локального параметра, в который будет записан созданный объект.

Порядок перебора наборов объектов в операторе `FOR` может быть задан блоком `ORDER`. Если в выражениях, задающих порядок, объявляется новый параметр (не встречавшийся ранее в опции `FOR` и в верхнем контексте), то при вычислении результирующего значения автоматически добавляется условие на не `NULL` всех этих выражений.

Основное действие указывается после ключевого слова `DO`, альтернативное может быть указано после ключевого слова `ELSE`.

В случае когда в операторе присутствует блок `NEW`, а условие не указывается, для созданного объекта будет вызвано основное действие.

### Параметры

- `expression`

    [Выражение](Expression.md), задающее условие. В этом выражении можно обращаться как к уже объявленным параметрам, так и объявлять новые локальные параметры.

- `DESC`

    Ключевое слово. Указывает на обратный порядок просмотра наборов объектов. 

- `orderExpr1, ..., orderExprK`

    Список выражений, определяющих порядок, в котором будут перебираться наборы объектов. Для определения порядка сначала используется значение первого выражения, затем при равенстве используется значение второго и т.д. Если список не задан, то перебор происходит в произвольном порядке.

- `alias`

    Имя локального параметра, которое будет соответствовать создаваемому объекту. [Простой идентификатор](IDs.md#id-broken).

- `className`

    Имя класса создаваемого объекта. Задается [идентификатором класса](IDs.md#classid-broken).

- `action`

    [Контекстно-зависимый оператор-действие](Action_operators.md#contextdependent), описывающий основное действие.

- `alternativeAction`

    Контекстно-зависимый оператор-действие, описывающий альтернативное действие. В качестве параметров нельзя использовать параметры, добавленные при задании условия / создании объекта.

### Примеры

```lsf
name = DATA STRING[100] (Store);

testFor  {
    LOCAL sum = INTEGER ();
    FOR iterate(i, 1, 100) DO {
        sum() <- sum() (+) i;
    }

    FOR in(Sku s) DO {
        MESSAGE 'Sku ' + id(s) + ' was selected';
    }

    FOR Store st IS Store DO { // пробегаем по всем объектам класса Store
        FOR in(st, Sku s) DO { // пробегаем по всем Sku, для которых in задано
            MESSAGE 'There is Sku ' + id(s) + ' in store ' + name(st);
        }

    }
}

newSku ()  {
    NEW s = Sku {
        id(s) <- 425;
        name(s) <- 'New Sku';
    }
}

copy (Sku old)  {
    NEW new = Sku {
        id(new) <- id(old);
        name(new) <- name(old);
    }
}

createDetails (Order o)  {
    FOR in(Sku s) NEW d = OrderDetail DO {
        order(d) <- o;
        sku(d) <- s;
    }
}
```